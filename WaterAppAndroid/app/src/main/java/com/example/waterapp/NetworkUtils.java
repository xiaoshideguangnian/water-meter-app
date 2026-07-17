package com.example.waterapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;

import com.opencsv.CSVWriter;

/**
 * 网络请求工具类，包含登录、获取数据、导出等功能。
 * 完全模拟 Python 版的 API 交互。
 */
public class NetworkUtils {

    private static final String TAG = "NetworkUtils";
    private static final String BASE_URL = "https://itwater.aql.cn";
    private static final String COOKIE_PREF = "cookies";

    private Context context;
    private Map<String, String> cookieStore = new HashMap<>();
    private boolean cookiesLoaded = false;

    // 单例
    private static NetworkUtils instance;

    public static synchronized NetworkUtils getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkUtils(context.getApplicationContext());
        }
        return instance;
    }

    private NetworkUtils(Context context) {
        this.context = context;
        loadCookies();
    }

    // ---------- Cookie 管理 ----------
    private void loadCookies() {
        SharedPreferences prefs = context.getSharedPreferences(COOKIE_PREF, Context.MODE_PRIVATE);
        String cookieStr = prefs.getString("cookies", "");
        if (!cookieStr.isEmpty()) {
            String[] pairs = cookieStr.split("; ");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    cookieStore.put(kv[0], kv[1]);
                }
            }
        }
        cookiesLoaded = true;
    }

    public void saveCookies() {
        SharedPreferences prefs = context.getSharedPreferences(COOKIE_PREF, Context.MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookieStore.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        prefs.edit().putString("cookies", sb.toString()).apply();
    }

    public void clearCookies() {
        cookieStore.clear();
        context.getSharedPreferences(COOKIE_PREF, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public boolean hasCookies() {
        loadCookies();
        return !cookieStore.isEmpty();
    }

    // ---------- HTTP 请求封装 ----------
    private HttpURLConnection createConnection(String urlString, String method) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        // 添加 Cookie
        if (!cookieStore.isEmpty()) {
            StringBuilder cookieStr = new StringBuilder();
            for (Map.Entry<String, String> entry : cookieStore.entrySet()) {
                if (cookieStr.length() > 0) cookieStr.append("; ");
                cookieStr.append(entry.getKey()).append("=").append(entry.getValue());
            }
            conn.setRequestProperty("Cookie", cookieStr.toString());
        }
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream is;
        if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
            is = new GZIPInputStream(conn.getInputStream());
        } else {
            is = conn.getInputStream();
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private byte[] readBytes(HttpURLConnection conn) throws IOException {
        InputStream is = conn.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        is.close();
        return baos.toByteArray();
    }

    // 更新 Cookie（从响应头）
    private void updateCookies(HttpURLConnection conn) {
        String headerName;
        for (int i = 1; (headerName = conn.getHeaderFieldKey(i)) != null; i++) {
            if ("Set-Cookie".equalsIgnoreCase(headerName)) {
                String cookie = conn.getHeaderField(i);
                if (cookie != null) {
                    String[] parts = cookie.split(";");
                    String[] kv = parts[0].split("=", 2);
                    if (kv.length == 2) {
                        cookieStore.put(kv[0].trim(), kv[1].trim());
                    }
                }
            }
        }
    }

    // ---------- 登录 ----------
    public boolean login(String custCode, String username, String password, String captcha) {
        try {
            // 1. 获取登录页面提取 VIEWSTATE 等
            HttpURLConnection conn = createConnection(BASE_URL + "/Login.aspx", "GET");
            conn.connect();
            updateCookies(conn);
            String html = readResponse(conn);
            Document doc = Jsoup.parse(html);

            String viewstate = getInputValue(doc, "__VIEWSTATE");
            String viewstategen = getInputValue(doc, "__VIEWSTATEGENERATOR");
            String eventvalidation = getInputValue(doc, "__EVENTVALIDATION");

            // 2. POST 登录
            String postUrl = BASE_URL + "/Login.aspx";
            HttpURLConnection postConn = createConnection(postUrl, "POST");
            postConn.setDoOutput(true);
            postConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            postConn.setRequestProperty("Referer", BASE_URL + "/Login.aspx");
            postConn.setRequestProperty("Origin", BASE_URL);

            StringBuilder data = new StringBuilder();
            appendParam(data, "__VIEWSTATE", viewstate);
            appendParam(data, "__VIEWSTATEGENERATOR", viewstategen);
            appendParam(data, "__EVENTVALIDATION", eventvalidation);
            appendParam(data, "txtCustCode", custCode);
            appendParam(data, "txtUserCode", username);
            appendParam(data, "txtPassword", password);
            appendParam(data, "txtVerifyCode", captcha);
            appendParam(data, "btnLogin", "");

            postConn.getOutputStream().write(data.toString().getBytes("UTF-8"));
            postConn.connect();
            updateCookies(postConn);

            // 检查是否有 WaterSupplyAuth cookie
            for (String key : cookieStore.keySet()) {
                if (key.equalsIgnoreCase("WaterSupplyAuth")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "登录异常", e);
            return false;
        }
    }

    // 测试自动登录（通过访问一个需要登录的页面）
    public boolean testAutoLogin() {
        try {
            // 尝试获取某个页面，如果返回200且包含特定标记则成功
            HttpURLConnection conn = createConnection(BASE_URL + "/Default.aspx?Tabid=2172", "GET");
            conn.connect();
            updateCookies(conn);
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                String html = readResponse(conn);
                // 简单判断是否包含表格或"登录"字样
                if (html.contains("WAF2216_grdStatDataList") || !html.contains("登录")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 获取验证码图片 ----------
    public byte[] fetchCaptcha() throws IOException {
        HttpURLConnection conn = createConnection(BASE_URL + "/common/Image.ashx", "GET");
        conn.connect();
        updateCookies(conn);
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            return readBytes(conn);
        }
        return null;
    }

    // ---------- 获取表格数据（模拟 fetch_table） ----------
    public List<WaterData> fetchTable(int tabid) {
        List<WaterData> result = new ArrayList<>();
        try {
            String baseUrl = BASE_URL + "/Default.aspx?Tabid=" + tabid;
            // 第一页
            HttpURLConnection conn = createConnection(baseUrl, "GET");
            conn.connect();
            updateCookies(conn);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "First page error: " + conn.getResponseCode());
                return null;
            }
            String html = readResponse(conn);
            Document doc = Jsoup.parse(html);

            // 提取固定字段
            Map<String, String> fields = extractHiddenFields(doc);
            // 添加必要字段
            fields.put("ScriptManager1", "ContentUpdatePanel|WAF2216$grdStatDataList");
            fields.put("__LASTFOCUS", "");
            // 日期字段用当前日期
            if (!fields.containsKey("WAF2216$dt1") || fields.get("WAF2216$dt1").isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                fields.put("WAF2216$dt1", sdf.format(new Date()));
            }

            // 解析第一页表格
            Element table = doc.selectFirst("table#WAF2216_grdStatDataList");
            if (table == null) {
                Log.e(TAG, "Table not found");
                return null;
            }
            // 获取表头（用于列名）
            Elements headerCells = table.select("tr:first-child th, tr:first-child td");
            List<String> headers = new ArrayList<>();
            for (Element cell : headerCells) {
                headers.add(cell.text().trim());
            }
            // 如果表头为空，尝试用列索引
            if (headers.isEmpty()) {
                for (int i = 0; i < 10; i++) headers.add("列" + (i+1));
            }

            // 解析数据行
            Elements rows = table.select("tr.rowstyle, tr:not(:first-child)");
            int totalPages = 1;
            // 获取总页数（从分页控件）
            Element pageSpan = doc.selectFirst("span#WAF2216_grdStatDataList_lblPageCount");
            if (pageSpan != null) {
                try {
                    totalPages = Integer.parseInt(pageSpan.text().trim());
                } catch (NumberFormatException ignored) {}
            } else {
                // 从 a 标签中提取最大页码
                int maxPage = 1;
                for (Element a : doc.select("a[href]")) {
                    String href = a.attr("href");
                    if (href.contains("Page$")) {
                        String num = href.replaceAll(".*Page\$(\d+).*", "$1");
                        try {
                            int p = Integer.parseInt(num);
                            if (p > maxPage) maxPage = p;
                        } catch (Exception ignored) {}
                    }
                }
                totalPages = Math.max(1, maxPage);
            }

            // 解析第一页数据
            parseTableRows(rows, headers, result);

            // 如果有更多页，循环请求
            for (int page = 2; page <= totalPages; page++) {
                boolean fetched = false;
                int retries = 3;
                while (!fetched && retries > 0) {
                    try {
                        Map<String, String> postData = new HashMap<>(fields);
                        postData.put("__EVENTTARGET", "WAF2216$grdStatDataList");
                        postData.put("__EVENTARGUMENT", "Page$" + page);
                        postData.put("__ASYNCPOST", "true");

                        HttpURLConnection postConn = createConnection(baseUrl, "POST");
                        postConn.setDoOutput(true);
                        postConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                        postConn.setRequestProperty("Referer", baseUrl);
                        postConn.setRequestProperty("X-MicrosoftAjax", "Delta=true");
                        postConn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                        postConn.setRequestProperty("Origin", BASE_URL);

                        StringBuilder sb = new StringBuilder();
                        for (Map.Entry<String, String> e : postData.entrySet()) {
                            if (sb.length() > 0) sb.append("&");
                            sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                                    .append("=")
                                    .append(URLEncoder.encode(e.getValue(), "UTF-8"));
                        }
                        postConn.getOutputStream().write(sb.toString().getBytes("UTF-8"));
                        postConn.connect();
                        updateCookies(postConn);
                        int code = postConn.getResponseCode();
                        if (code == HttpURLConnection.HTTP_OK) {
                            String responseText = readResponse(postConn);
                            if (responseText.startsWith("0|error|")) {
                                // 可能视图状态失效，尝试刷新第一页获取新状态
                                if (retries == 1) {
                                    // 重新获取第一页更新 fields
                                    HttpURLConnection refreshConn = createConnection(baseUrl, "GET");
                                    refreshConn.connect();
                                    updateCookies(refreshConn);
                                    String freshHtml = readResponse(refreshConn);
                                    Document freshDoc = Jsoup.parse(freshHtml);
                                    fields = extractHiddenFields(freshDoc);
                                    // 更新 postData 中的字段
                                }
                                retries--;
                                continue;
                            }
                            // 提取表格 HTML
                            String tableHtml = extractTableHtml(responseText);
                            if (tableHtml != null) {
                                Document tableDoc = Jsoup.parse(tableHtml);
                                Elements pageRows = tableDoc.select("tr.rowstyle, tr:not(:first-child)");
                                parseTableRows(pageRows, headers, result);
                                fetched = true;
                            } else {
                                retries--;
                            }
                        } else {
                            retries--;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Page " + page + " error", e);
                        retries--;
                    }
                }
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "fetchTable error", e);
            return null;
        }
    }

    private void parseTableRows(Elements rows, List<String> headers, List<WaterData> result) {
        for (Element tr : rows) {
            Elements tds = tr.select("td");
            if (tds.isEmpty()) continue;
            Map<String, String> rowMap = new HashMap<>();
            for (int i = 0; i < tds.size() && i < headers.size(); i++) {
                rowMap.put(headers.get(i), tds.get(i).text().trim());
            }
            // 提取水表编号（尝试多种列名）
            String meterId = "";
            for (String key : rowMap.keySet()) {
                if (key.contains("水表编号") || key.contains("表号") || key.equals("MeterCode")) {
                    meterId = rowMap.get(key);
                    break;
                }
            }
            // 如果没有找到，用第一列
            if (meterId.isEmpty() && !rowMap.isEmpty()) {
                meterId = rowMap.values().iterator().next();
            }
            result.add(new WaterData(rowMap, meterId));
        }
    }

    private Map<String, String> extractHiddenFields(Document doc) {
        Map<String, String> fields = new HashMap<>();
        for (Element input : doc.select("input")) {
            String name = input.attr("name");
            if (!name.isEmpty() && !name.equals("__VIEWSTATE") && !name.equals("__EVENTVALIDATION")
                    && !name.equals("__VIEWSTATEGENERATOR") && !name.equals("__ASYNCPOST")) {
                fields.put(name, input.val());
            }
        }
        for (Element select : doc.select("select")) {
            String name = select.attr("name");
            if (!name.isEmpty()) {
                Element selected = select.selectFirst("option[selected]");
                fields.put(name, selected != null ? selected.val() : "");
            }
        }
        return fields;
    }

    private String extractTableHtml(String response) {
        // 用正则提取 table 标签
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "<table[^>]*id="WAF2216_grdStatDataList"[^>]*>.*?</table>",
                java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(response);
        if (m.find()) {
            return m.group(0);
        }
        return null;
    }

    private String getInputValue(Document doc, String name) {
        Element el = doc.selectFirst("input[name='" + name + "']");
        return el != null ? el.val() : "";
    }

    private void appendParam(StringBuilder sb, String key, String value) throws IOException {
        if (sb.length() > 0) sb.append("&");
        sb.append(URLEncoder.encode(key, "UTF-8"))
                .append("=")
                .append(URLEncoder.encode(value, "UTF-8"));
    }

    // ---------- 水表详情 ----------
    public String fetchMeterDetail(String meterId) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String today = sdf.format(new Date());
            String url = BASE_URL + "/default.aspx?tabcode=WS0409&prevtab=2043&id=" + meterId
                    + "&prevTabCode=&bt=&et=&Page=0&areaid=810&Gardenid=33946&t=" + today
                    + "&code=" + meterId;
            HttpURLConnection conn = createConnection(url, "GET");
            conn.connect();
            updateCookies(conn);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return "获取详情失败";
            }
            String html = readResponse(conn);
            Document doc = Jsoup.parse(html);

            StringBuilder sb = new StringBuilder();
            sb.append("========== 水表 ").append(meterId).append(" 详情 ==========\n\n");

            // 提取基本信息（span id 后缀）
            String[] ids = {"lblCode", "lblMeterCode", "lblMeterSize", "lblInstDate", "lblinstaddr",
                    "lblConcentrator", "lblCollector", "lblState", "lblCheckDate", "lblCheckTerm",
                    "lblNetaddr", "lblUseFor"};
            String[] labels = {"用户编号", "水表编号", "水表口径", "安装日期", "用水地址", "集中器",
                    "采集器", "水表状态", "检定日期", "有效期至", "网络地址", "水表用途"};
            for (int i = 0; i < ids.length; i++) {
                Element span = doc.selectFirst("span[id$='" + ids[i] + "']");
                String val = span != null ? span.text().trim() : "";
                if (!val.isEmpty()) {
                    sb.append(labels[i]).append(": ").append(val).append("\n");
                }
            }

            // 抄表记录
            Element table = doc.selectFirst("table#WAF2082_gvReadMeterList");
            if (table != null) {
                sb.append("\n【抄表记录】\n");
                Elements headerTh = table.select("tr.headerstyle th");
                List<String> headers = new ArrayList<>();
                for (Element th : headerTh) headers.add(th.text().trim());
                if (headers.isEmpty()) {
                    // 尝试第一行 td
                    Elements firstRow = table.select("tr:first-child td");
                    for (Element td : firstRow) headers.add(td.text().trim());
                }
                // 列宽对齐（简化）
                sb.append(String.join(" | ", headers)).append("\n");
                sb.append("----------------------------------------\n");
                for (Element tr : table.select("tr.rowstyle")) {
                    List<String> row = new ArrayList<>();
                    for (Element td : tr.select("td")) {
                        row.add(td.text().trim());
                    }
                    sb.append(String.join(" | ", row)).append("\n");
                }
            } else {
                sb.append("\n【抄表记录】无数据\n");
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "fetchMeterDetail error", e);
            return "获取详情失败: " + e.getMessage();
        }
    }

    // ---------- 单抄 ----------
    public String singleRead(String meterId) {
        try {
            long timestamp = System.currentTimeMillis();
            String url = BASE_URL + "/MeterWater/GetDemoRead.aspx?MeterCode=" + meterId + "&timeStamp=" + timestamp;
            HttpURLConnection conn = createConnection(url, "GET");
            conn.connect();
            updateCookies(conn);
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return readResponse(conn);
            } else {
                return "HTTP " + conn.getResponseCode();
            }
        } catch (Exception e) {
            Log.e(TAG, "singleRead error", e);
            return "单抄失败: " + e.getMessage();
        }
    }

    // ---------- 导出 CSV ----------
    public String exportCSV(List<WaterData> data) {
        try {
            File dir = new File(context.getExternalFilesDir(null), "exports");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "water_data_" + System.currentTimeMillis() + ".csv";
            File file = new File(dir, fileName);

            CSVWriter writer = new CSVWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            // 写入表头（从第一条数据取键）
            if (!data.isEmpty()) {
                Map<String, String> first = data.get(0).getRowData();
                writer.writeNext(first.keySet().toArray(new String[0]));
                for (WaterData item : data) {
                    writer.writeNext(item.getRowData().values().toArray(new String[0]));
                }
            }
            writer.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "exportCSV error", e);
            return null;
        }
    }
}
