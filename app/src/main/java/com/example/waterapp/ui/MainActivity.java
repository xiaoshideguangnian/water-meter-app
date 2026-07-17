package com.example.waterapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import com.example.waterapp.R;
import com.example.waterapp.network.ApiClient;
import com.example.waterapp.utils.L;
import com.example.waterapp.model.DataTable;
import com.opencsv.CSVWriter;

public class MainActivity extends AppCompatActivity {
    private EditText etTabid;
    private Button btnRefresh, btnExportCsv;
    private RecyclerView rvData;
    private TextView tvStatus, tvLog;
    private DataAdapter adapter;
    private DataTable currentTable;
    private String currentTabid = "2172";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        loadData("2172");
    }

    private void initViews() {
        etTabid = findViewById(R.id.et_tabid);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnExportCsv = findViewById(R.id.btn_export_csv);
        rvData = findViewById(R.id.rv_data);
        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        etTabid.setText(currentTabid);

        rvData.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DataAdapter(new ArrayList<>());
        rvData.setAdapter(adapter);

        btnRefresh.setOnClickListener(v -> {
            String tab = etTabid.getText().toString().trim();
            if (TextUtils.isEmpty(tab) || !TextUtils.isDigitsOnly(tab)) {
                Toast.makeText(this, "请输入有效TabID", Toast.LENGTH_SHORT).show();
                return;
            }
            loadData(tab);
        });

        btnExportCsv.setOnClickListener(v -> exportCsv());
        
        // 长按监听复制行
        adapter.setOnItemLongClickListener((row, pos) -> {
            StringBuilder sb = new StringBuilder();
            for (String cell : row) {
                sb.append(cell).append("\t");
            }
            L.log("复制行: " + sb.toString());
            Toast.makeText(this, "已复制行数据", Toast.LENGTH_SHORT).show();
            return true;
        });

        adapter.setOnItemClickListener((row, pos) -> {
            // 双击处理：若包含水表编号则打开详情
            for (String val : row) {
                if (val != null && val.matches("\\d+")) {
                    showMeterDetail(val);
                    break;
                }
            }
        });
    }

    private void loadData(String tabid) {
        tvStatus.setText("正在加载数据...");
        new Thread(() -> {
            try {
                DataTable table = ApiClient.fetchTable(Integer.parseInt(tabid));
                runOnUiThread(() -> {
                    if (table != null) {
                        currentTable = table;
                        adapter.setData(table.rows);
                        tvStatus.setText(String.format("共 %d 行，显示 %d 行", table.totalRows, table.rows.size()));
                        L.log("TabID " + tabid + " 加载完成，共 " + table.totalRows + " 行");
                    } else {
                        tvStatus.setText("加载失败，请查看日志");
                        L.log("TabID " + tabid + " 返回空数据");
                    }
                });
            } catch (Exception e) {
                L.e("加载数据失败", e);
                runOnUiThread(() -> tvStatus.setText("错误: " + e.getMessage()));
            }
        }).start();
    }

    private void exportCsv() {
        if (currentTable == null || currentTable.rows.isEmpty()) {
            Toast.makeText(this, "无数据可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File exportDir = getExternalFilesDir(null);
            File file = new File(exportDir, "export_" + System.currentTimeMillis() + ".csv");
            CSVWriter writer = new CSVWriter(new FileWriter(file));
            // 写表头
            writer.writeNext(currentTable.headers.toArray(new String[0]));
            for (List<String> row : currentTable.rows) {
                writer.writeNext(row.toArray(new String[0]));
            }
            writer.close();
            L.log("导出成功: " + file.getAbsolutePath());
            Toast.makeText(this, "已导出到 " + file.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            L.e("导出CSV失败", e);
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMeterDetail(String meterId) {
        Intent intent = new Intent(this, MeterDetailActivity.class);
        intent.putExtra("meter_id", meterId);
        startActivity(intent);
    }
}
