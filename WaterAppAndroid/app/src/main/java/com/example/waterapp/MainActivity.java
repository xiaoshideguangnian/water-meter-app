package com.example.waterapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private EditText etTabid;
    private Button btnRefresh, btnExport, btnLogout;
    private TextView tvDataStatus, tvRowCount;
    private RecyclerView recyclerView;
    private DataAdapter adapter;
    private NetworkUtils networkUtils;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<WaterData> currentData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        networkUtils = NetworkUtils.getInstance(this);

        etTabid = findViewById(R.id.et_tabid);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnExport = findViewById(R.id.btn_export);
        btnLogout = findViewById(R.id.btn_logout);
        tvDataStatus = findViewById(R.id.tv_data_status);
        tvRowCount = findViewById(R.id.tv_row_count);
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DataAdapter(this);
        recyclerView.setAdapter(adapter);

        // 点击条目打开详情
        adapter.setOnItemClickListener((view, position) -> {
            if (currentData != null && position < currentData.size()) {
                WaterData item = currentData.get(position);
                Intent intent = new Intent(MainActivity.this, DetailActivity.class);
                intent.putExtra("meter_id", item.getMeterId());
                startActivity(intent);
            }
        });

        btnRefresh.setOnClickListener(v -> refreshData());
        btnExport.setOnClickListener(v -> exportCSV());
        btnLogout.setOnClickListener(v -> logout());

        // 自动加载数据
        refreshData();

        // 请求存储权限（Android 6+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void refreshData() {
        String tabidStr = etTabid.getText().toString().trim();
        if (TextUtils.isEmpty(tabidStr) || !TextUtils.isDigitsOnly(tabidStr)) {
            Toast.makeText(this, "TabID 必须为数字", Toast.LENGTH_SHORT).show();
            return;
        }
        final int tabid = Integer.parseInt(tabidStr);

        tvDataStatus.setText(R.string.status_loading);
        tvDataStatus.setTextColor(getColor(android.R.color.holo_blue_dark));
        tvRowCount.setText("");

        new Thread(() -> {
            List<WaterData> data = networkUtils.fetchTable(tabid);
            mainHandler.post(() -> {
                if (data == null) {
                    tvDataStatus.setText("加载失败");
                    tvDataStatus.setTextColor(getColor(R.color.red));
                    Toast.makeText(MainActivity.this, "数据加载失败，请检查网络或登录", Toast.LENGTH_LONG).show();
                    return;
                }
                currentData = data;
                adapter.setData(data);
                tvDataStatus.setText("共 " + data.size() + " 行");
                tvDataStatus.setTextColor(getColor(R.color.gray_text));
                tvRowCount.setText("行数: " + data.size());
            });
        }).start();
    }

    private void exportCSV() {
        if (currentData == null || currentData.isEmpty()) {
            Toast.makeText(this, "没有数据可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请先授予存储权限", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            String path = networkUtils.exportCSV(currentData);
            mainHandler.post(() -> {
                if (path != null) {
                    Toast.makeText(MainActivity.this, "已导出到: " + path, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "导出失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void logout() {
        networkUtils.clearCookies();
        currentData = null;
        adapter.setData(null);
        tvDataStatus.setText("已退出");
        tvRowCount.setText("");
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "存储权限被拒绝，无法导出", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
