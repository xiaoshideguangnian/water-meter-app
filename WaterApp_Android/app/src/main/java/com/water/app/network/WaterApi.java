package com.water.app.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
java.util.concurrent.TimeUnit;
java.util.regex.Matcher;
java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WaterApi {
    private static final String TAG = "WaterApi";
    public static final String BASE_URL = "https://itwater.aql.cn";
    private static final String COOKIE_KEY = "saved_cookies";

    private final OkHttpClient client;
    private final SharedPreferences prefs;

    public WaterApi(Context context) {
        prefs = context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE);
        client = new OkHttpClient.Builder()
                .cookieJar(new MyCookieJar())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    // 保存 Cookie 到 SharedPreferences
    private class MyCookieJar implements CookieJar {
        private CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            if (cookies != null && !cookies.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Cookie c : cookies) {
                    sb.append(c.name()).append("=").append(c.value()).append("; ");
                }
                String cookieStr = sb.toString();
                if (cookieStr.endsWith("; ")) cookieStr = cookieStr.substring(0, cookieStr.length() - 2);
                prefs.edit().putString(COOKIE_KEY, cookieStr).apply();
                Log.d(TAG, "保存Cookie: " + cookieStr);
                // 同时更新 CookieManager
                java.net.CookieManager cm = new java.net.CookieManager();
                cm.getCookieStore().add(URI.create(url.toString()),
                        java.net.HttpCookie.parse(cookieStr).toArray(new java.net.HttpCookie[0]));
                this.cookieManager = cm;
            }
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> cookies = new ArrayList<>();
            String saved = prefs.getString(COOKIE_KEY, null);
            if (saved != null) {
                String[] pairs = saved.split("; ");
                for (String pair : pairs) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        cookies.add(Cookie.parse(url, kv[0] + "=" + kv[1]));
                    }
                }
            }
            return cookies;
        }
    }

    // 检查是否已登录（尝试请求测试页面）
    public boolean checkLogin() {
        Request req = new Request.Builder().url(BASE_URL + "/Default.aspx?Tabid=2172").build();
        try (Response resp = client.newCall(req).execute()) {
            return resp.isSuccessful() && resp.body() != null &&
                    resp.body().string().contains("WaterSupplyAuth");
        } catch (IOException e) {
            Log.e(TAG, "检查登录失败", e);
            return false;
        }
    }

    // 获取验证码图片字节
    public byte[] getCaptchaImage() throws IOException {
        Request req = new Request.Builder().url(BASE_URL + "/common/Image.ashx").build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("验证码请求失败");
            return resp.body().bytes();
        }
    }

    // 登录
    public LoginResult login(String custCode, String username, String password, String captcha)
            throws IOException {
        // 先访问登录页，提取隐藏字段
        Request getReq = new Request.Builder().url(BASE_URL + "/Login.aspx").build();
        Document doc;
        try (Response getResp = client.newCall(getReq).execute()) {
            doc = Jsoup.parse(getResp.body().string());
        }

        String viewstate = doc.select("input[name=__VIEWSTATE]").val();
        String viewstategen = doc.select("input[name=__VIEWSTATEGENERATOR]").val();
        String eventvalidation = doc.select("input[name=__EVENTVALIDATION]").val();

        FormBody body = new FormBody.Builder()
                .add("__VIEWSTATE", viewstate)
                .add("__VIEWSTATEGENERATOR", viewstategen)
                .add("__EVENTVALIDATION", eventvalidation)
                .add("txtCustCode", custCode)
                .add("txtUserCode", username)
                .add("txtPassword", password)
                .add("txtVerifyCode", captcha)
                .add("btnLogin", "")
                .build();

        Request postReq = new Request.Builder()
                .url(BASE_URL + "/Login.aspx")
                .header("Referer", BASE_URL + "/Login.aspx")
                .header("Origin", BASE_URL)
                .post(body)
                .build();

        try (Response postResp = client.newCall(postReq).execute()) {
            String respStr = postResp.body().string();
            // 检查 Cookie 中是否有 WaterSupplyAuth
            List<Cookie> cookies = client.cookieJar().loadForRequest(HttpUrl.parse(BASE_URL));
            for (Cookie c : cookies) {
                if ("WaterSupplyAuth".equals(c.name())) {
                    return new LoginResult(true, null);
                }
            }
            // 解析错误信息
            Document errDoc = Jsoup.parse(respStr);
            String errMsg = errDoc.select("span#lblError").text();
            if (errMsg.isEmpty()) errMsg = "登录失败，请检查用户名、密码或验证码。";
            return new LoginResult(false, errMsg);
        }
    }

    // 获取数据表（分页，返回 List<List<String>> 和表头）
    public TableData fetchTable(int tabid) throws IOException {
        String baseUrl = BASE_URL + "/Default.aspx?Tabid=" + tabid;
        Document doc;
        // 第一次请求
        Request req = new Request.Builder().url(baseUrl).build();
        try (Response resp = client.newCall(req).execute()) {
            doc = Jsoup.parse(resp.body().string());
        }
        Elements tables = doc.select("table#WAF2216_grdStatDataList");
        if (tables.isEmpty()) {
            // 尝试通用解析
            Elements allTables = doc.select("table");
            // 简单返回第一个表格
            if (!allTables.isEmpty()) {
                return parseTableFromJsoup(allTables.first());
            }
            throw new IOException("未找到目标表格");
        }

        // 表头
        List<String> headers = new ArrayList<>();
        Elements ths = tables.first().select("tr.headerstyle th");
        for (org.jsoup.nodes.Element th : ths) {
            headers.add(th.text().trim());
        }

        // 第一页数据
        List<List<String>> allRows = new ArrayList<>();
        for (org.jsoup.nodes.Element tr : tables.first().select("tr.rowstyle")) {
            List<String> row = new ArrayList<>();
            for (org.jsoup.nodes.Element td : tr.select("td")) {
                row.add(td.text().trim());
            }
            // 对齐
            while (row.size() < headers.size()) row.add("");
            if (row.size() > headers.size()) row = row.subList(0, headers.size());
            allRows.add(row);
        }

        // 提取分页信息
        int totalPages = 1;
        Pattern pagePat = Pattern.compile("Page\$(\d+)");
        Matcher m = pagePat.matcher(doc.html());
        while (m.find()) {
            int p = Integer.parseInt(m.group(1));
            if (p > totalPages) totalPages = p;
        }

        // 提取固定字段（POST 异步分页用）
        Map<String, String> fixedFields = new HashMap<>();
        String[] fieldNames = {"__VIEWSTATE","__EVENTVALIDATION","__VIEWSTATEGENERATOR",
                "WAF2216$lstAreaList","WAF2216$lstOption","WAF2216$lstTheSize",
                "WAF2216$txtUserCode","WAF2216$dt1","WAF2216$ReportType"};
        for (String name : fieldNames) {
            org.jsoup.nodes.Element el = doc.select("input[name=" + name + "]").first();
            if (el == null) el = doc.select("select[name=" + name + "] option[selected]").first();
            if (el != null) fixedFields.put(name, el.val());
        }
        fixedFields.putIfAbsent("WAF2216$lstAreaList", "810");
        fixedFields.putIfAbsent("WAF2216$lstOption", "0");
        fixedFields.putIfAbsent("WAF2216$lstTheSize", "15");
        fixedFields.putIfAbsent("WAF2216$dt1", "2026-07-06");
        fixedFields.put("ScriptManager1", "ContentUpdatePanel|WAF2216$grdStatDataList");
        fixedFields.put("__LASTFOCUS", "");

        // 异步请求后续页
        for (int page = 2; page <= totalPages; page++) {
            FormBody.Builder postBody = new FormBody.Builder();
            for (Map.Entry<String, String> e : fixedFields.entrySet()) {
                postBody.add(e.getKey(), e.getValue() != null ? e.getValue() : "");
            }
            postBody.add("__EVENTTARGET", "WAF2216$grdStatDataList");
            postBody.add("__EVENTARGUMENT", "Page$" + page);
            postBody.add("__ASYNCPOST", "true");

            Request postReq = new Request.Builder()
                    .url(baseUrl)
                    .header("Referer", baseUrl)
                    .header("Origin", BASE_URL)
                    .header("X-MicrosoftAjax", "Delta=true")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .post(postBody.build())
                    .build();

            try (Response postResp = client.newCall(postReq).execute()) {
                String respText = postResp.body().string();
                if (respText.startsWith("0|error|")) {
                    Log.w(TAG, "第" + page + "页返回错误，跳过");
                    continue;
                }
                // 提取表格部分
                Pattern tablePat = Pattern.compile("<table[^>]*id=\"WAF2216_grdStatDataList\"[^>]*>.*?</table>",
                        Pattern.DOTALL);
                Matcher tableMat = tablePat.matcher(respText);
                if (tableMat.find()) {
                    String tableHtml = tableMat.group();
                    Document pageDoc = Jsoup.parse(tableHtml);
                    for (org.jsoup.nodes.Element tr : pageDoc.select("tr.rowstyle")) {
                        List<String> row = new ArrayList<>();
                        for (org.jsoup.nodes.Element td : tr.select("td")) {
                            row.add(td.text().trim());
                        }
                        while (row.size() < headers.size()) row.add("");
                        if (row.size() > headers.size()) row = row.subList(0, headers.size());
                        allRows.add(row);
                    }
                } else {
                    Log.w(TAG, "第" + page + "页未找到表格内容");
                }
            }
        }
        return new TableData(headers, allRows);
    }

    // 获取水表详情页 HTML（用于详情展示）
    public String getMeterDetail(String meterId) throws IOException {
        String today = java.time.LocalDate.now().toString();
        String url = BASE_URL + "/default.aspx?tabcode=WS0409&prevtab=2043"
                + "&id=" + meterId + "&prevTabCode=&bt=&et=&Page=0&areaid=810"
                + "&Gardenid=33946&t=" + today + "&code=" + meterId;
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            return resp.body().string();
        }
    }

    // 单抄请求
    public String singleRead(String meterId) throws IOException {
        long ts = System.currentTimeMillis();
        String url = BASE_URL + "/MeterWater/GetDemoRead.aspx?MeterCode=" + meterId + "&timeStamp=" + ts;
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            return resp.body().string();
        }
    }

    // 工具方法：从 Jsoup 元素解析表格
    private TableData parseTableFromJsoup(org.jsoup.nodes.Element table) {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        Elements ths = table.select("tr:first-child td, tr:first-child th");
        for (org.jsoup.nodes.Element th : ths) headers.add(th.text().trim());
        for (org.jsoup.nodes.Element tr : table.select("tr")) {
            if (tr.select("th").isEmpty()) {
                List<String> row = new ArrayList<>();
                for (org.jsoup.nodes.Element td : tr.select("td")) row.add(td.text().trim());
                if (!row.isEmpty()) rows.add(row);
            }
        }
        return new TableData(headers, rows);
    }

    // 内部数据类
    public static class LoginResult {
        public final boolean success;
        public final String message;
        public LoginResult(boolean s, String m) { success = s; message = m; }
    }

    public static class TableData {
        public final List<String> headers;
        public final List<List<String>> rows;
        public TableData(List<String> h, List<List<String>> r) { headers = h; rows = r; }
    }
}
