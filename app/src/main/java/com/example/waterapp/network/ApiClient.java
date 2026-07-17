package com.example.waterapp.network;

import android.content.Context;
import android.content.SharedPreferences;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.waterapp.model.DataTable;
import com.example.waterapp.utils.L;

public class ApiClient {
    private static OkHttpClient client;
    private static PersistentCookieJar cookieJar;
    private static final String BASE_URL = "https://itwater.aql.cn";
    private static SharedPreferences prefs;
    private static final String PREFS_NAME = "cookies_prefs";

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cookieJar = new PersistentCookieJar(new SetCookieCache(), new SharedPrefsCookiePersistor(context));
        client = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public static OkHttpClient getClient() {
        return client;
    }

    public static boolean hasCookie() {
        return !cookieJar.loadForRequest(HttpUrl.get(BASE_URL + "/")).isEmpty();
    }

    public static void clearCookies() {
        cookieJar.clear();
        prefs.edit().clear().apply();
    }

    public static void saveCookies() {
        // PersistentCookieJar already saves automatically, nothing to do
    }

    public static byte[] getCaptchaBytes() throws IOException {
        Request request = new Request.Builder().url(BASE_URL + "/common/Image.ashx").build();
        try (Response resp = client.newCall(request).execute()) {
            if (resp.isSuccessful() && resp.body() != null) {
                return resp.body().bytes();
            }
        }
        return null;
    }

    public static boolean login(String custCode, String username, String password, String captcha) throws IOException {
        // Logout first
        client.newCall(new Request.Builder().url(BASE_URL + "/Logout.aspx").build()).execute().close();

        // Get login page
        Request getReq = new Request.Builder().url(BASE_URL + "/Login.aspx").build();
        String pageHtml;
        try (Response r = client.newCall(getReq).execute()) {
            pageHtml = r.body().string();
        }

        Document doc = Jsoup.parse(pageHtml);
        Map<String, String> formData = new HashMap<>();
        formData.put("__VIEWSTATE", doc.select("input[name=__VIEWSTATE]").val());
        formData.put("__VIEWSTATEGENERATOR", doc.select("input[name=__VIEWSTATEGENERATOR]").val());
        formData.put("__EVENTVALIDATION", doc.select("input[name=__EVENTVALIDATION]").val());
        formData.put("txtCustCode", custCode);
        formData.put("txtUserCode", username);
        formData.put("txtPassword", password);
        formData.put("txtVerifyCode", captcha);
        formData.put("btnLogin", "");

        FormBody.Builder builder = new FormBody.Builder();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }

        Request loginReq = new Request.Builder()
                .url(BASE_URL + "/Login.aspx")
                .header("Referer", BASE_URL + "/Login.aspx")
                .header("Origin", BASE_URL)
                .post(builder.build())
                .build();

        try (Response resp = client.newCall(loginReq).execute()) {
            String body = resp.body().string();
            // Check if WaterSupplyAuth cookie exists
            for (Cookie cookie : cookieJar.loadForRequest(HttpUrl.get(BASE_URL + "/"))) {
                if (cookie.name().equals("WaterSupplyAuth")) return true;
            }
            L.log("登录响应: " + body.substring(0, Math.min(500, body.length())));
        }
        return false;
    }

    public static boolean verifySession(int tabid) throws IOException {
        DataTable table = fetchTable(tabid);
        return table != null;
    }

    public static DataTable fetchTable(int tabid) throws IOException {
        String baseUrl = BASE_URL + "/Default.aspx?Tabid=" + tabid;
        Request getReq = new Request.Builder().url(baseUrl).build();
        String firstPageHtml;
        try (Response r = client.newCall(getReq).execute()) {
            firstPageHtml = r.body().string();
        }

        Document soup = Jsoup.parse(firstPageHtml);
        Element table = soup.selectFirst("table#WAF2216_grdStatDataList");
        if (table == null) {
            // try find any table
            Elements tables = soup.select("table");
            if (!tables.isEmpty()) {
                // simple parse
                return parseSimpleTable(tables.first());
            }
            return null;
        }

        // Extract fixed fields
        Map<String, String> fixed = new HashMap<>();
        String[] fields = {"__VIEWSTATE", "__EVENTVALIDATION", "__VIEWSTATEGENERATOR",
                "WAF2216$lstAreaList", "WAF2216$lstOption", "WAF2216$lstTheSize",
                "WAF2216$txtUserCode", "WAF2216$dt1", "WAF2216$ReportType"};
        for (String field : fields) {
            Element el = soup.selectFirst("input[name='" + field + "']");
            if (el != null) fixed.put(field, el.val());
            else {
                Element sel = soup.selectFirst("select[name='" + field + "']");
                if (sel != null) {
                    Element opt = sel.selectFirst("option[selected]");
                    fixed.put(field, opt != null ? opt.val() : "");
                }
            }
        }
        fixed.put("ScriptManager1", "ContentUpdatePanel|WAF2216$grdStatDataList");
        fixed.put("__LASTFOCUS", "");

        // Headers
        Element headerRow = table.selectFirst("tr.headerstyle");
        List<String> headers = new ArrayList<>();
        if (headerRow != null) {
            for (Element th : headerRow.select("th")) {
                headers.add(th.text().trim());
            }
        }

        // First page rows
        List<List<String>> allRows = new ArrayList<>();
        for (Element tr : table.select("tr.rowstyle")) {
            List<String> row = new ArrayList<>();
            for (Element td : tr.select("td")) {
                row.add(td.text().trim());
            }
            if (!row.isEmpty()) allRows.add(row);
        }

        // Find total pages
        Set<Integer> pages = new HashSet<>();
        for (Element a : soup.select("a[href]")) {
            Matcher m = Pattern.compile("Page\\$(\\d+)").matcher(a.attr("href"));
            if (m.find()) pages.add(Integer.parseInt(m.group(1)));
        }
        int totalPages = pages.isEmpty() ? 1 : Collections.max(pages);
        L.log("检测到总页数: " + totalPages);

        if (totalPages > 1) {
            for (int p = 2; p <= totalPages; p++) {
                Map<String, String> postData = new HashMap<>(fixed);
                postData.put("__EVENTTARGET", "WAF2216$grdStatDataList");
                postData.put("__EVENTARGUMENT", "Page$" + p);
                postData.put("__ASYNCPOST", "true");

                FormBody.Builder postBuilder = new FormBody.Builder();
                for (Map.Entry<String, String> e : postData.entrySet()) {
                    postBuilder.add(e.getKey(), e.getValue());
                }

                Request postReq = new Request.Builder()
                        .url(baseUrl)
                        .header("Referer", baseUrl)
                        .header("Origin", BASE_URL)
                        .header("X-MicrosoftAjax", "Delta=true")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .post(postBuilder.build())
                        .build();

                try (Response r = client.newCall(postReq).execute()) {
                    String respText = r.body().string();
                    if (respText.startsWith("0|error|")) {
                        L.e("第 " + p + " 页返回错误: " + respText);
                        continue;
                    }
                    // Parse table from response (simplified)
                    Document frag = Jsoup.parseBodyFragment(respText);
                    Element tbl = frag.selectFirst("table#WAF2216_grdStatDataList");
                    if (tbl == null) {
                        // try to extract using regex
                        Matcher m = Pattern.compile("<table[^>]*id=\"WAF2216_grdStatDataList\"[^>]*>(.*?)</table>",
                                Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(respText);
                        if (m.find()) {
                            tbl = Jsoup.parseBodyFragment(m.group(0)).selectFirst("table");
                        }
                    }
                    if (tbl != null) {
                        for (Element tr : tbl.select("tr.rowstyle")) {
                            List<String> row = new ArrayList<>();
                            for (Element td : tr.select("td")) row.add(td.text().trim());
                            if (!row.isEmpty()) allRows.add(row);
                        }
                        L.log("第 " + p + " 页数据获取成功");
                    } else {
                        L.e("第 " + p + " 页未找到表格");
                    }
                } catch (Exception e) {
                    L.e("第 " + p + " 页请求异常", e);
                }
            }
        }

        DataTable dt = new DataTable();
        dt.headers = headers;
        dt.rows = allRows;
        dt.totalRows = allRows.size();
        return dt;
    }

    private static DataTable parseSimpleTable(Element table) {
        // fallback
        DataTable dt = new DataTable();
        dt.headers = new ArrayList<>();
        dt.rows = new ArrayList<>();
        Elements rows = table.select("tr");
        boolean headerDone = false;
        for (Element tr : rows) {
            List<String> cells = new ArrayList<>();
            for (Element td : tr.select("td,th")) {
                cells.add(td.text().trim());
            }
            if (!headerDone && tr.select("th").size() > 0) {
                dt.headers = cells;
                headerDone = true;
            } else if (!cells.isEmpty()) {
                dt.rows.add(cells);
            }
        }
        dt.totalRows = dt.rows.size();
        return dt;
    }

    public static String getMeterDetail(String meterId) throws IOException {
        String today = java.time.LocalDate.now().toString();
        String url = BASE_URL + "/default.aspx?tabcode=WS0409&prevtab=2043&id=" + meterId +
                "&prevTabCode=&bt=&et=&Page=0&areaid=810&Gardenid=33946&t=" + today + "&code=" + meterId;
        Request req = new Request.Builder().url(url).build();
        String html;
        try (Response r = client.newCall(req).execute()) {
            html = r.body().string();
        }
        Document doc = Jsoup.parse(html);
        StringBuilder sb = new StringBuilder();
        sb.append("=== 水表详情 (").append(meterId).append(") ===\n");
        String[] ids = {"lblCode", "lblMeterCode", "lblMeterSize", "lblInstDate", "lblinstaddr",
                "lblConcentrator", "lblCollector", "lblState", "lblCheckDate", "lblCheckTerm",
                "lblNetaddr", "lblUseFor"};
        String[] labels = {"用户编号", "水表编号", "水表口径", "安装日期", "用水地址", "集中器",
                "采集器", "水表状态", "检定日期", "有效期至", "网络地址", "水表用途"};
        for (int i = 0; i < ids.length; i++) {
            Element span = doc.selectFirst("span[id$=" + ids[i] + "]");
            if (span != null && !span.text().isEmpty()) {
                sb.append(labels[i]).append(": ").append(span.text()).append("\n");
            }
        }
        Element recordTable = doc.selectFirst("table#WAF2082_gvReadMeterList");
        if (recordTable != null) {
            sb.append("\n=== 抄表记录 ===\n");
            for (Element tr : recordTable.select("tr")) {
                for (Element td : tr.select("td,th")) {
                    sb.append(td.text()).append("\t");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static String singleReadMeter(String meterId) throws IOException {
        long ts = System.currentTimeMillis();
        String url = BASE_URL + "/MeterWater/GetDemoRead.aspx?MeterCode=" + meterId + "&timeStamp=" + ts;
        Request req = new Request.Builder().url(url).build();
        try (Response r = client.newCall(req).execute()) {
            return r.body().string();
        }
    }
}
