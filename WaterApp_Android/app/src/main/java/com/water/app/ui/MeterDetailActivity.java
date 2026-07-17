package com.water.app.ui;

import android.os.AsyncTask;
import android.os.Bundle;
android.text.TextUtils;
android.view.View;
android.widget.Button;
android.widget.ScrollView;
android.widget.TextView;
android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.water.app.R;
import com.water.app.network.WaterApi;
import com.water.app.util.LogUtil;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class MeterDetailActivity extends AppCompatActivity {
    private TextView tvContent;
    private Button btnSingleRead;
    private String meterId;
    private WaterApi api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meter_detail);
        tvContent = findViewById(R.id.tv_content);
        btnSingleRead = findViewById(R.id.btn_single_read);
        api = new WaterApi(this);
        meterId = getIntent().getStringExtra("meter_id");

        loadDetail();

        btnSingleRead.setOnClickListener(v -> doSingleRead());
    }

    private void loadDetail() {
        new AsyncTask<Void, Void, String>() {
            private String error;
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    return api.getMeterDetail(meterId);
                } catch (Exception e) {
                    error = e.getMessage();
                    return null;
                }
            }
            @Override
            protected void onPostExecute(String html) {
                if (html == null) {
                    Toast.makeText(MeterDetailActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                    return;
                }
                parseAndDisplay(html);
            }
        }.execute();
    }

    private void parseAndDisplay(String html) {
        Document doc = Jsoup.parse(html);
        StringBuilder sb = new StringBuilder();
        // 基本信息
        String[] ids = {"lblCode","lblMeterCode","lblMeterSize","lblInstDate","lblinstaddr",
                "lblConcentrator","lblCollector","lblState","lblCheckDate","lblCheckTerm","lblNetaddr","lblUseFor"};
        String[] labels = {"用户编号","水表编号","水表口径","安装日期","用水地址","集中器","采集器",
                "水表状态","检定日期","有效期至","网络地址","水表用途"};
        sb.append("【基本信息】\n");
        for (int i = 0; i < ids.length; i++) {
            Element span = doc.select("span[id$=" + ids[i] + "]").first();
            String val = span != null ? span.text().trim() : "";
            if (!val.isEmpty()) {
                sb.append(labels[i]).append(": ").append(val).append("\n");
            }
        }
        sb.append("\n【抄表记录】\n");
        Element table = doc.select("table#WAF2082_gvReadMeterList").first();
        if (table != null) {
            Elements thead = table.select("tr.headerstyle th");
            if (!thead.isEmpty()) {
                for (Element th : thead) sb.append(th.text()).append("\t");
                sb.append("\n");
            }
            for (Element tr : table.select("tr.rowstyle")) {
                for (Element td : tr.select("td")) sb.append(td.text()).append("\t");
                sb.append("\n");
            }
        } else {
            sb.append("无抄表记录");
        }
        tvContent.setText(sb.toString());
    }

    private void doSingleRead() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    return api.singleRead(meterId);
                } catch (Exception e) {
                    return "单抄失败: " + e.getMessage();
                }
            }
            @Override
            protected void onPostExecute(String result) {
                tvContent.append("\n\n【单抄结果】\n" + result);
            }
        }.execute();
    }
}
