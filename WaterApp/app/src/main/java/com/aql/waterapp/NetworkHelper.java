
package com.aql.waterapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkHelper {
    private static final String BASE_URL = "https://itwater.aql.cn";
    private static final String COOKIE_PREF = "cookies";
    private static final String COOKIE_KEY = "cookie_string";

    private static NetworkHelper instance;
    private SharedPreferences prefs;
    private String cookieString = "";

    private NetworkHelper(Context context) {
        prefs = context.getSharedPreferences(COOKIE_PREF, Context.MODE_PRIVATE);
        cookieString = prefs.getString(COOKIE_KEY, "");
    }

    public static synchronized NetworkHelper getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkHelper(context.getApplicationContext());
        }
        return instance;
    }

    public void saveCookies(String cookie) {
        cookieString = cookie;
        prefs.edit().putString(COOKIE_KEY, cookie).apply();
    }

    public void clearCookies() {
        cookieString = "";
        prefs.edit().remove(COOKIE_KEY).apply();
    }

    private HttpURLConnection getConnection(String urlString, String method, Map<String, String> headers) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoInput(true);
        if (method.equals("POST")) {
            conn.setDoOutput(true);
        }
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");
        if (cookieString != null && !cookieString.isEmpty()) {
            conn.setRequestProperty("Cookie", cookieString);
        }
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream in;
        try {
            in = conn.getInputStream();
        } catch (IOException e) {
            in = conn.getErrorStream();
        }
        if (in == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private Map<String, String> getCookies(HttpURLConnection conn) {
        Map<String, String> cookies = new HashMap<>();
        String headerName;
        for (int i = 1; (headerName = conn.getHeaderFieldKey(i)) != null; i++) {
            if (headerName.equalsIgnoreCase("Set-Cookie")) {
                String cookie = conn.getHeaderField(i);
                if (cookie != null) {
                    // 取第一个分号前的部分
                    String[] parts = cookie.split(";");
                    if (parts.length > 0) {
                        String[] kv = parts[0].trim().split("=", 2);
                        if (kv.length == 2) {
                            cookies.put(kv[0], kv[1]);
                        }
                    }
                }
            }
        }
        return cookies;
    }

    private void updateCookieFromResponse(HttpURLConnection conn) {
        Map<String, String> cookies = getCookies(conn);
        if (!cookies.isEmpty()) {
            StringBuilder sb = new StringBuilder(cookieString);
            for (Map.Entry<String, String> e : cookies.entrySet()) {
                String pair = e.getKey() + "=" + e.getValue();
                if (cookieString.isEmpty()) {
                    sb.append(pair);
                } else {
                    // 替换或追加
                    int idx = cookieString.indexOf(e.getKey() + "=");
                    if (idx >= 0) {
                        int end = cookieString.indexOf(";", idx);
                        if (end < 0) end = cookieString.length();
                        sb.replace(idx, end, pair);
                    } else {
                        sb.append("; ").append(pair);
                    }
                }
            }
            cookieString = sb.toString();
            saveCookies(cookieString);
        }
    }

    // 登录
    public interface LoginCallback {
        void onResult(boolean success, String errorMsg);
    }

    public void login(String custCode, String username, String password, String captchaText, LoginCallback callback) {
        new Thread(() -> {
            try {
                // 先访问Login.aspx获取viewstate
                HttpURLConnection conn = getConnection(BASE_URL + "/Login.aspx", "GET", null);
                conn.connect();
                String html = readResponse(conn);
                updateCookieFromResponse(conn);
                conn.disconnect();

                Document doc = Jsoup.parse(html);
                String viewState = val(doc.select("input[name=__VIEWSTATE]").first());
                String viewStateGen = val(doc.select("input[name=__VIEWSTATEGENERATOR]").first());
                String eventValidation = val(doc.select("input[name=__EVENTVALIDATION]").first());

                Map<String, String> params = new HashMap<>();
                params.put("__VIEWSTATE", viewState);
                params.put("__VIEWSTATEGENERATOR", viewStateGen);
                params.put("__EVENTVALIDATION", eventValidation);
                params.put("txtCustCode", custCode);
                params.put("txtUserCode", username);
                params.put("txtPassword", password);
                params.put("txtVerifyCode", captchaText);
                params.put("btnLogin", "");

                String postData = buildPostData(params);

                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/x-www-form-urlencoded");
                headers.put("Referer", BASE_URL + "/Login.aspx");
                headers.put("Origin", BASE_URL);

                conn = getConnection(BASE_URL + "/Login.aspx", "POST", headers);
                conn.setInstanceFollowRedirects(true);
                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();
                String respHtml = readResponse(conn);
                updateCookieFromResponse(conn);
                conn.disconnect();

                // 检查cookie中是否含有WaterSupplyAuth
                if (cookieString.contains("WaterSupplyAuth")) {
                    callback.onResult(true, null);
                } else {
                    Document errDoc = Jsoup.parse(respHtml);
                    Element errSpan = errDoc.select("span#lblError").first();
                    String errMsg = errSpan != null ? errSpan.text() : "登录失败，请检查用户名、密码或验证码。";
                    callback.onResult(false, errMsg);
                }
            } catch (Exception e) {
                callback.onResult(false, "网络异常: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private String val(Element e) {
        return e == null ? "" : e.val();
    }

    private String buildPostData(Map<String, String> params) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
              .append("=")
              .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        return sb.toString();
    }

    // 获取验证码图片
    public Bitmap getCaptchaImage() throws IOException {
        HttpURLConnection conn = getConnection(BASE_URL + "/common/Image.ashx", "GET", null);
        conn.connect();
        InputStream in = conn.getInputStream();
        Bitmap bitmap = BitmapFactory.decodeStream(in);
        in.close();
        updateCookieFromResponse(conn);
        conn.disconnect();
        return bitmap;
    }

    // 获取表格数据
    public interface FetchCallback {
        void onResult(List<String> headers, List<List<String>> rows, String error);
    }

    public void fetchTable(int tabid, FetchCallback callback) {
        new Thread(() -> {
            try {
                String baseUrl = BASE_URL + "/Default.aspx?Tabid=" + tabid;
                HttpURLConnection conn = getConnection(baseUrl, "GET", null);
                conn.connect();
                String html = readResponse(conn);
                updateCookieFromResponse(conn);
                conn.disconnect();

                Document doc = Jsoup.parse(html);
                Element table = doc.select("table#WAF2216_grdStatDataList").first();
                if (table == null) {
                    // 尝试解析整个页面表格
                    callback.onResult(null, null, "未找到目标表格");
                    return;
                }

                // 提取固定字段
                Map<String, String> fixed = new HashMap<>();
                for (Element inp : doc.select("input")) {
                    String name = inp.attr("name");
                    if (name.equals("__VIEWSTATE") || name.equals("__EVENTVALIDATION") || name.equals("__VIEWSTATEGENERATOR") ||
                        name.equals("WAF2216$lstAreaList") || name.equals("WAF2216$lstOption") ||
                        name.equals("WAF2216$lstTheSize") || name.equals("WAF2216$txtUserCode") ||
                        name.equals("WAF2216$dt1") || name.equals("WAF2216$ReportType")) {
                        fixed.put(name, inp.val());
                    }
                }
                for (Element sel : doc.select("select")) {
                    String name = sel.attr("name");
                    if (name.equals("WAF2216$lstAreaList") || name.equals("WAF2216$lstOption") || name.equals("WAF2216$lstTheSize")) {
                        Element opt = sel.select("option[selected]").first();
                        fixed.put(name, opt != null ? opt.val() : "");
                    }
                }
                fixed.put("ScriptManager1", "ContentUpdatePanel|WAF2216$grdStatDataList");
                fixed.put("__LASTFOCUS", "");
                // 确保关键字段有默认值
                if (!fixed.containsKey("WAF2216$lstAreaList") || fixed.get("WAF2216$lstAreaList").isEmpty())
                    fixed.put("WAF2216$lstAreaList", "810");
                if (!fixed.containsKey("WAF2216$lstOption") || fixed.get("WAF2216$lstOption").isEmpty())
                    fixed.put("WAF2216$lstOption", "0");
                if (!fixed.containsKey("WAF2216$lstTheSize") || fixed.get("WAF2216$lstTheSize").isEmpty())
                    fixed.put("WAF2216$lstTheSize", "15");
                if (!fixed.containsKey("WAF2216$dt1") || fixed.get("WAF2216$dt1").isEmpty())
                    fixed.put("WAF2216$dt1", "2026-07-06");

                // 表头
                Element headerRow = table.select("tr.headerstyle").first();
                if (headerRow == null) {
                    callback.onResult(null, null, "无法识别表头");
                    return;
                }
                List<String> headers = new ArrayList<>();
                for (Element th : headerRow.select("th")) {
                    headers.add(th.text().trim());
                }

                // 第一页数据
                List<List<String>> allRows = new ArrayList<>();
                for (Element tr : table.select("tr.rowstyle")) {
                    List<String> row = new ArrayList<>();
                    for (Element td : tr.select("td")) {
                        row.add(td.text().trim());
                    }
                    if (!row.isEmpty()) allRows.add(row);
                }

                // 提取总页数
                int totalPages = 1;
                Pattern p = Pattern.compile("Page\\$(\\d+)");
                for (Element a : doc.select("a[href]")) {
                    Matcher m = p.matcher(a.attr("href"));
                    if (m.find()) {
                        int pg = Integer.parseInt(m.group(1));
                        if (pg > totalPages) totalPages = pg;
                    }
                }
                // 也检查span中的数字
                for (Element span : doc.select("span")) {
                    String txt = span.text().trim();
                    if (txt.matches("\\d+")) {
                        int pg = Integer.parseInt(txt);
                        if (pg > totalPages) totalPages = pg;
                    }
                }

                if (totalPages > 1) {
                    for (int page = 2; page <= totalPages; page++) {
                        Map<String, String> postData = new HashMap<>(fixed);
                        postData.put("__EVENTTARGET", "WAF2216$grdStatDataList");
                        postData.put("__EVENTARGUMENT", "Page$" + page);
                        postData.put("__ASYNCPOST", "true");

                        String data = buildPostData(postData);
                        Map<String, String> headersPost = new HashMap<>();
                        headersPost.put("Content-Type", "application/x-www-form-urlencoded");
                        headersPost.put("Referer", baseUrl);
                        headersPost.put("Origin", BASE_URL);
                        headersPost.put("X-MicrosoftAjax", "Delta=true");
                        headersPost.put("X-Requested-With", "XMLHttpRequest");

                        HttpURLConnection postConn = getConnection(baseUrl, "POST", headersPost);
                        OutputStream os = postConn.getOutputStream();
                        os.write(data.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        os.close();
                        String respText = readResponse(postConn);
                        updateCookieFromResponse(postConn);
                        postConn.disconnect();

                        // 提取表格
                        Pattern tablePattern = Pattern.compile("<table[^>]*id=\"WAF2216_grdStatDataList\"[^>]*>.*?</table>", Pattern.DOTALL);
                        Matcher tableMatcher = tablePattern.matcher(respText);
                        if (tableMatcher.find()) {
                            String tableHtml = tableMatcher.group();
                            Document tableDoc = Jsoup.parse(tableHtml);
                            for (Element tr : tableDoc.select("tr.rowstyle")) {
                                List<String> row = new ArrayList<>();
                                for (Element td : tr.select("td")) {
                                    row.add(td.text().trim());
                                }
                                if (!row.isEmpty()) allRows.add(row);
                            }
                        } else {
                            // 可能出错，继续
                            Logger.log("第" + page + "页未找到表格，响应前500字符：" + respText.substring(0, Math.min(500, respText.length())));
                        }
                    }
                }

                callback.onResult(headers, allRows, null);

            } catch (Exception e) {
                callback.onResult(null, null, "网络错误: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // 水表详情
    public interface DetailCallback {
        void onResult(String detailText, String error);
    }

    public void fetchMeterDetail(String meterId, DetailCallback callback) {
        new Thread(() -> {
            try {
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
                String url = BASE_URL + "/default.aspx?tabcode=WS0409&prevtab=2043&id=" + meterId +
                        "&prevTabCode=&bt=&et=&Page=0&areaid=810&Gardenid=33946&t=" + today + "&code=" + meterId;
                HttpURLConnection conn = getConnection(url, "GET", null);
                conn.connect();
                String html = readResponse(conn);
                updateCookieFromResponse(conn);
                conn.disconnect();

                Document doc = Jsoup.parse(html);

                // 提取基本信息
                StringBuilder sb = new StringBuilder();
                sb.append("水表详情 (编号: ").append(meterId).append(")\n");
                sb.append("============================================================\n\n");
                sb.append("【基本信息】\n");
                String[] ids = {"lblCode", "lblMeterCode", "lblMeterSize", "lblInstDate", "lblinstaddr",
                        "lblConcentrator", "lblCollector", "lblState", "lblCheckDate", "lblCheckTerm",
                        "lblNetaddr", "lblUseFor"};
                String[] labels = {"用户编号", "水表编号", "水表口径", "安装日期", "用水地址",
                        "集中器", "采集器", "水表状态", "检定日期", "有效期至",
                        "网络地址", "水表用途"};
                for (int i = 0; i < ids.length; i++) {
                    Element span = doc.select("span[id$=" + ids[i] + "]").first();
                    if (span != null && !span.text().isEmpty()) {
                        sb.append("  ").append(labels[i]).append(": ").append(span.text()).append("\n");
                    }
                }

                // 抄表记录表格
                Element table = doc.select("table#WAF2082_gvReadMeterList").first();
                if (table != null) {
                    sb.append("\n【抄表记录】\n");
                    Element thead = table.select("tr.headerstyle").first();
                    if (thead != null) {
                        List<String> headers = new ArrayList<>();
                        for (Element th : thead.select("th")) {
                            headers.add(th.text().trim());
                        }
                        // 简单表格显示
                        List<List<String>> rows = new ArrayList<>();
                        for (Element tr : table.select("tr.rowstyle")) {
                            List<String> row = new ArrayList<>();
                            for (Element td : tr.select("td")) {
                                row.add(td.text().trim());
                            }
                            if (!row.isEmpty()) rows.add(row);
                        }
                        // 用固定宽度显示
                        int[] colWidths = new int[headers.size()];
                        for (int i = 0; i < headers.size(); i++) colWidths[i] = headers.get(i).length();
                        for (List<String> row : rows) {
                            for (int i = 0; i < row.size() && i < colWidths.length; i++) {
                                if (row.get(i).length() > colWidths[i]) colWidths[i] = row.get(i).length();
                            }
                        }
                        String sep = "+";
                        for (int w : colWidths) sep += "-".repeat(w + 2) + "+";
                        sb.append(sep).append("\n");
                        String headerLine = "|";
                        for (int i = 0; i < headers.size(); i++) {
                            String fmt = " %-" + colWidths[i] + "s |";
                            headerLine += String.format(fmt, headers.get(i));
                        }
                        sb.append(headerLine).append("\n");
                        sb.append(sep).append("\n");
                        for (List<String> row : rows) {
                            String line = "|";
                            for (int i = 0; i < row.size() && i < colWidths.length; i++) {
                                String fmt = " %-" + colWidths[i] + "s |";
                                line += String.format(fmt, row.get(i));
                            }
                            sb.append(line).append("\n");
                        }
                        sb.append(sep);
                    }
                } else {
                    sb.append("\n【抄表记录】无数据");
                }

                callback.onResult(sb.toString(), null);

            } catch (Exception e) {
                callback.onResult(null, "获取详情失败: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // 单抄
    public interface SingleReadCallback {
        void onResult(String result, String error);
    }

    public void singleRead(String meterId, SingleReadCallback callback) {
        new Thread(() -> {
            try {
                long ts = System.currentTimeMillis();
                String url = BASE_URL + "/MeterWater/GetDemoRead.aspx?MeterCode=" + meterId + "&timeStamp=" + ts;
                HttpURLConnection conn = getConnection(url, "GET", null);
                conn.connect();
                String resp = readResponse(conn);
                updateCookieFromResponse(conn);
                conn.disconnect();
                callback.onResult(resp, null);
            } catch (Exception e) {
                callback.onResult(null, "单抄失败: " + e.getMessage());
            }
        }).start();
    }
}
