package com.water.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.water.app.R;
import com.water.app.network.WaterApi;
import com.water.app.util.LogUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
java.util.ArrayList;
java.util.Collections;
java.util.Comparator;
java.util.List;

public class MainActivity extends AppCompatActivity {
    private EditText etTabid;
    private TableLayout tableLayout;
    private ScrollView scrollVertical;
    private ScrollView scrollHorizontal;
    private TextView tvStatus, tvLog;
    private WaterApi api;
    private List<String> headers = new ArrayList<>();
    private List<List<String>> allRows = new ArrayList<>();
    private List<List<String>> displayRows = new ArrayList<>(); // 显示的行（排序后）
    private int maxDisplayRows = 100;
    private boolean showAll = false;
    private String sortCol = null;
    private boolean sortAsc = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        api = new WaterApi(this);
        etTabid = findViewById(R.id.et_tabid);
        tableLayout = findViewById(R.id.table_layout);
        scrollVertical = findViewById(R.id.scroll_vertical);
        scrollHorizontal = findViewById(R.id.scroll_horizontal);
        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        Button btnRefresh = findViewById(R.id.btn_refresh);
        Button btnExportCsv = findViewById(R.id.btn_export_csv);
        Button btnExportExcel = findViewById(R.id.btn_export_excel);
        Button btnLogout = findViewById(R.id.btn_logout);
        Button btnToggleLog = findViewById(R.id.btn_toggle_log);
        View logPanel = findViewById(R.id.log_panel);
        Button btnCopyLog = findViewById(R.id.btn_copy_log);
        Button btnClearLog = findViewById(R.id.btn_clear_log);

        // 日志监听
        LogUtil.setListener(msg -> runOnUiThread(() -> {
            tvLog.append(msg + "\n");
        }));

        btnRefresh.setOnClickListener(v -> refreshData());
        btnExportCsv.setOnClickListener(v -> exportCSV());
        btnExportExcel.setOnClickListener(v -> exportExcel());
        btnLogout.setOnClickListener(v -> logout());
        btnToggleLog.setOnClickListener(v -> {
            logPanel.setVisibility(logPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        btnCopyLog.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("log", LogUtil.getAll()));
            Toast.makeText(this, "日志已复制", Toast.LENGTH_SHORT).show();
        });
        btnClearLog.setOnClickListener(v -> {
            LogUtil.clear();
            tvLog.setText("");
        });

        // 初始加载
        etTabid.setText("2172");
        refreshData();
    }

    private void refreshData() {
        String tabid = etTabid.getText().toString().trim();
        if (tabid.isEmpty()) {
            Toast.makeText(this, "请输入TabID", Toast.LENGTH_SHORT).show();
            return;
        }
        tvStatus.setText("正在加载...");
        LogUtil.add("开始加载 TabID=" + tabid);
        new AsyncTask<Void, Void, WaterApi.TableData>() {
            private String errorMsg = null;
            @Override
            protected WaterApi.TableData doInBackground(Void... voids) {
                try {
                    return api.fetchTable(Integer.parseInt(tabid));
                } catch (Exception e) {
                    errorMsg = e.getMessage();
                    LogUtil.add("加载失败: " + errorMsg);
                    return null;
                }
            }
            @Override
            protected void onPostExecute(WaterApi.TableData data) {
                if (data == null) {
                    tvStatus.setText("加载失败: " + errorMsg);
                    return;
                }
                headers = data.headers;
                allRows = data.rows;
                showAll = false;
                updateDisplay();
                tvStatus.setText("共 " + allRows.size() + " 行（显示前 " + maxDisplayRows + " 行）");
                LogUtil.add("加载成功，共 " + allRows.size() + " 行");
            }
        }.execute();
    }

    private void updateDisplay() {
        int end = showAll ? allRows.size() : Math.min(allRows.size(), maxDisplayRows);
        displayRows = new ArrayList<>(allRows.subList(0, end));
        if (sortCol != null) {
            sortTable(sortCol, sortAsc);
        }
        buildTable();
    }

    private void buildTable() {
        tableLayout.removeAllViews();
        // 添加表头行
        TableRow headerRow = new TableRow(this);
        headerRow.setBackgroundColor(0xFFE8EDF3);
        for (int i = 0; i < headers.size(); i++) {
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setText(headers.get(i));
            tv.setPadding(8, 8, 8, 8);
            tv.setTextColor(0xFF1A1A1A);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setOnClickListener(v -> onHeaderClick(idx));
            headerRow.addView(tv);
        }
        tableLayout.addView(headerRow);

        // 数据行
        for (List<String> row : displayRows) {
            TableRow tr = new TableRow(this);
            for (int i = 0; i < headers.size(); i++) {
                TextView tv = new TextView(this);
                String val = i < row.size() ? row.get(i) : "";
                tv.setText(val);
                tv.setPadding(8, 4, 8, 4);
                tv.setTextColor(0xFF333333);
                // 双击/长按事件 稍后绑定
                final String cellValue = val;
                tv.setOnClickListener(v -> onCellClick(row, i));
                tv.setOnLongClickListener(v -> {
                    showRowMenu(v, row);
                    return true;
                });
                tr.addView(tv);
            }
            tableLayout.addView(tr);
        }
    }

    private void onHeaderClick(int colIdx) {
        if (colIdx < headers.size()) {
            String colName = headers.get(colIdx);
            if (colName.equals(sortCol)) {
                sortAsc = !sortAsc;
            } else {
                sortCol = colName;
                sortAsc = true;
            }
            sortTable(sortCol, sortAsc);
            buildTable();
            tvStatus.setText("已按「" + colName + "」" + (sortAsc ? "升序" : "降序") + "排序");
        }
    }

    private void sortTable(String colName, boolean asc) {
        int idx = headers.indexOf(colName);
        if (idx < 0 || displayRows.isEmpty()) return;
        Comparator<List<String>> comp = (a, b) -> {
            String va = idx < a.size() ? a.get(idx) : "";
            String vb = idx < b.size() ? b.get(idx) : "";
            // 尝试数值比较
            try {
                double da = Double.parseDouble(va.replace(",", ""));
                double db = Double.parseDouble(vb.replace(",", ""));
                return Double.compare(da, db);
            } catch (NumberFormatException e) {
                return va.compareTo(vb);
            }
        };
        if (!asc) comp = Collections.reverseOrder(comp);
        Collections.sort(displayRows, comp);
    }

    private void onCellClick(List<String> row, int colIdx) {
        String header = colIdx < headers.size() ? headers.get(colIdx) : "";
        if (header.contains("水表编号") || header.contains("表号")) {
            String meterId = colIdx < row.size() ? row.get(colIdx).trim() : "";
            if (!meterId.isEmpty()) {
                Intent intent = new Intent(this, MeterDetailActivity.class);
                intent.putExtra("meter_id", meterId);
                startActivity(intent);
            }
        }
    }

    private void showRowMenu(View anchor, List<String> row) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("复制行").setOnMenuItemClickListener(item -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < headers.size(); i++) {
                sb.append(headers.get(i)).append(": ").append(i < row.size() ? row.get(i) : "").append("\n");
            }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("row", sb.toString()));
            Toast.makeText(this, "已复制行信息", Toast.LENGTH_SHORT).show();
            return true;
        });
        popup.show();
    }

    private void exportCSV() {
        if (allRows.isEmpty()) {
            Toast.makeText(this, "无数据", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = getExternalFilesDir(null);
            File file = new File(dir, "data.csv");
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            // BOM
            writer.write("\uFEFF");
            writer.write(String.join(",", headers) + "\n");
            for (List<String> row : allRows) {
                List<String> escaped = new ArrayList<>();
                for (String cell : row) {
                    escaped.add("\"" + cell.replace("\"", "\"\"") + "\"");
                }
                writer.write(String.join(",", escaped) + "\n");
            }
            writer.close();
            // 分享
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/csv");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "导出 CSV"));
            LogUtil.add("CSV 已导出: " + file.getAbsolutePath());
        } catch (Exception e) {
            LogUtil.add("导出CSV失败: " + e.getMessage());
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportExcel() {
        // 若需要，可使用 Apache POI，此处简化为弹出提示或导出 CSV
        Toast.makeText(this, "Excel 导出功能待实现，可用 CSV 代替", Toast.LENGTH_SHORT).show();
        exportCSV();
    }

    private void logout() {
        // 清除 Cookie
        getSharedPreferences("water_prefs", MODE_PRIVATE).edit().clear().apply();
        LogUtil.add("已退出登录");
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
