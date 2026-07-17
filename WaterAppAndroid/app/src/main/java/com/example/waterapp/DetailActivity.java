package com.example.waterapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

public class DetailActivity extends AppCompatActivity {

    private TextView tvDetail;
    private Button btnSingleRead;
    private String meterId;
    private NetworkUtils networkUtils;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        networkUtils = NetworkUtils.getInstance(this);
        tvDetail = findViewById(R.id.tv_detail);
        btnSingleRead = findViewById(R.id.btn_single_read);

        meterId = getIntent().getStringExtra("meter_id");
        if (meterId == null) {
            finish();
            return;
        }

        setTitle("水表 " + meterId);
        loadDetail();

        btnSingleRead.setOnClickListener(v -> doSingleRead());
    }

    private void loadDetail() {
        tvDetail.setText("加载中...");
        new Thread(() -> {
            String detail = networkUtils.fetchMeterDetail(meterId);
            mainHandler.post(() -> tvDetail.setText(detail != null ? detail : "加载失败"));
        }).start();
    }

    private void doSingleRead() {
        btnSingleRead.setEnabled(false);
        btnSingleRead.setText("读取中...");
        new Thread(() -> {
            String result = networkUtils.singleRead(meterId);
            mainHandler.post(() -> {
                btnSingleRead.setEnabled(true);
                btnSingleRead.setText("单抄");
                if (result != null) {
                    // 追加到详情末尾
                    String current = tvDetail.getText().toString();
                    tvDetail.setText(current + "\n\n--- 单抄结果 ---\n" + result);
                } else {
                    Toast.makeText(DetailActivity.this, "单抄失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}
