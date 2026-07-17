
package com.aql.waterapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class DetailActivity extends AppCompatActivity {
    private TextView tvContent;
    private Button btnSingleRead;
    private String meterId;
    private NetworkHelper networkHelper;
    private String detailText = "";

    public static void start(Context ctx, String meterId) {
        Intent i = new Intent(ctx, DetailActivity.class);
        i.putExtra("meterId", meterId);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detail_activity);

        Toolbar toolbar = findViewById(R.id.toolbar_detail);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvContent = findViewById(R.id.tv_detail_content);
        tvContent.setMovementMethod(new ScrollingMovementMethod());
        btnSingleRead = findViewById(R.id.btn_single_read);

        networkHelper = NetworkHelper.getInstance(this);
        meterId = getIntent().getStringExtra("meterId");
        if (meterId == null) finish();

        setTitle("水表 " + meterId);

        loadDetail();

        btnSingleRead.setOnClickListener(v -> doSingleRead());
    }

    private void loadDetail() {
        tvContent.setText("加载中...");
        networkHelper.fetchMeterDetail(meterId, (detail, error) -> {
            runOnUiThread(() -> {
                if (error != null) {
                    tvContent.setText("获取详情失败: " + error);
                    Toast.makeText(DetailActivity.this, error, Toast.LENGTH_LONG).show();
                } else {
                    detailText = detail;
                    tvContent.setText(detail);
                }
            });
        });
    }

    private void doSingleRead() {
        btnSingleRead.setEnabled(false);
        btnSingleRead.setText("读取中...");
        networkHelper.singleRead(meterId, (result, error) -> {
            runOnUiThread(() -> {
                btnSingleRead.setEnabled(true);
                btnSingleRead.setText("单抄");
                if (error != null) {
                    Toast.makeText(DetailActivity.this, "单抄失败: " + error, Toast.LENGTH_SHORT).show();
                } else {
                    String append = "\n\n==================================================\n" +
                            "单抄结果 (" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "):\n" +
                            result;
                    detailText += append;
                    tvContent.setText(detailText);
                    // 滚动到底部
                    tvContent.post(() -> tvContent.scrollTo(0, tvContent.getHeight()));
                }
            });
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
