package com.example.waterapp.ui;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;
import com.example.waterapp.R;
import com.example.waterapp.network.ApiClient;
import com.example.waterapp.utils.L;

public class MeterDetailActivity extends AppCompatActivity {
    private TextView tvDetail, tvLog;
    private Button btnSingleRead;
    private String meterId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meter_detail);
        meterId = getIntent().getStringExtra("meter_id");
        tvDetail = findViewById(R.id.tv_detail);
        tvLog = findViewById(R.id.tv_log);
        btnSingleRead = findViewById(R.id.btn_single_read);
        setTitle("水表 " + meterId);
        loadDetail();
        btnSingleRead.setOnClickListener(v -> singleRead());
    }

    private void loadDetail() {
        new Thread(() -> {
            try {
                String info = ApiClient.getMeterDetail(meterId);
                runOnUiThread(() -> tvDetail.setText(info));
            } catch (Exception e) {
                L.e("获取详情失败", e);
                runOnUiThread(() -> tvDetail.setText("错误: " + e.getMessage()));
            }
        }).start();
    }

    private void singleRead() {
        new Thread(() -> {
            try {
                String result = ApiClient.singleReadMeter(meterId);
                runOnUiThread(() -> {
                    String log = tvLog.getText() + "\n" + result;
                    tvLog.setText(log);
                });
            } catch (Exception e) {
                L.e("单抄失败", e);
                runOnUiThread(() -> {
                    tvLog.setText(tvLog.getText() + "\n单抄失败: " + e.getMessage());
                });
            }
        }).start();
    }
}
