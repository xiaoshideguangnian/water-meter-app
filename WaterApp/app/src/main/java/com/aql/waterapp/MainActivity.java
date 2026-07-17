
package com.aql.waterapp;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private View loginContainer, mainContainer;
    private EditText etCust, etUser, etPwd, etCaptcha, etTabid;
    private ImageView ivCaptcha;
    private Button btnLogin, btnRefresh, btnLogout, btnExport, btnRefreshCaptcha, btnShowLogs;
    private TextView tvLoginStatus, tvStatus, tvRowCount;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private DataAdapter adapter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private NetworkHelper networkHelper;
    private List<String> currentHeaders = new ArrayList<>();
    private List<List<String>> currentRows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        networkHelper = NetworkHelper.getInstance(this);

        loginContainer = findViewById(R.id.login_container);
        mainContainer = findViewById(R.id.main_container);

        etCust = findViewById(R.id.et_cust_code);
        etUser = findViewById(R.id.et_username);
        etPwd = findViewById(R.id.et_password);
        etCaptcha = findViewById(R.id.et_captcha);
        ivCaptcha = findViewById(R.id.iv_captcha);
        btnLogin = findViewById(R.id.btn_login);
        btnRefreshCaptcha = findViewById(R.id.btn_refresh_captcha);
        btnShowLogs = findViewById(R.id.btn_show_logs);
        tvLoginStatus = findViewById(R.id.tv_login_status);

        etTabid = findViewById(R.id.et_tabid);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnLogout = findViewById(R.id.btn_logout);
        btnExport = findViewById(R.id.btn_export);
        tvStatus = findViewById(R.id.tv_status);
        tvRowCount = findViewById(R.id.tv_row_count);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        recyclerView = findViewById(R.id.recycler_view);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("数据查询");

        adapter = new DataAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // 点击行进入详情
        adapter.setOnItemClickListener((pos) -> {
            if (pos < currentRows.size()) {
                List<String> row = currentRows.get(pos);
                // 寻找水表编号列（可能列名包含"水表编号"或"表号"）
                int meterIdx = -1;
                for (int i = 0; i < currentHeaders.size(); i++) {
                    String h = currentHeaders.get(i);
                    if (h.contains("水表编号") || h.contains("表号")) {
                        meterIdx = i;
                        break;
                    }
                }
                if (meterIdx >= 0 && meterIdx < row.size()) {
                    String meterId = row.get(meterIdx).trim();
                    if (!meterId.isEmpty()) {
                        DetailActivity.start(MainActivity.this, meterId);
                    } else {
                        Toast.makeText(MainActivity.this, "未找到水表编号", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "未找到水表编号列", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnLogin.setOnClickListener(v -> doLogin());
        btnRefreshCaptcha.setOnClickListener(v -> loadCaptcha());
        btnShowLogs.setOnClickListener(v -> showLogsDialog());
        btnRefresh.setOnClickListener(v -> refreshData());
        btnLogout.setOnClickListener(v -> logout());
        btnExport.setOnClickListener(v -> exportCSV());
        swipeRefresh.setOnRefreshListener(this::refreshData);

        // 自动尝试登录（如果有cookies）
        checkAutoLogin();

        // 初始加载验证码
        loadCaptcha();
    }

    private void checkAutoLogin() {
        // 检查是否有cookie
        if (!networkHelper.cookieString.isEmpty()) {
            // 尝试获取数据来验证
            showMain(true);
            refreshData();
        } else {
            showMain(false);
        }
    }

    private void showMain(boolean show) {
        if (show) {
            loginContainer.setVisibility(View.GONE);
            mainContainer.setVisibility(View.VISIBLE);
        } else {
            loginContainer.setVisibility(View.VISIBLE);
            mainContainer.setVisibility(View.GONE);
        }
    }

    private void loadCaptcha() {
        new Thread(() -> {
            try {
                Bitmap bmp = networkHelper.getCaptchaImage();
                mainHandler.post(() -> ivCaptcha.setImageBitmap(bmp));
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "验证码加载失败", Toast.LENGTH_SHORT).show());
                Logger.logError("验证码加载失败: " + e.getMessage());
            }
        }).start();
    }

    private void doLogin() {
        String cust = etCust.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String pwd = etPwd.getText().toString().trim();
        String captcha = etCaptcha.getText().toString().trim();

        if (TextUtils.isEmpty(cust) || TextUtils.isEmpty(user) || TextUtils.isEmpty(pwd) || TextUtils.isEmpty(captcha)) {
            Toast.makeText(this, "请完整填写登录信息", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");
        tvLoginStatus.setText("正在登录…");

        networkHelper.login(cust, user, pwd, captcha, (success, error) -> {
            mainHandler.post(() -> {
                btnLogin.setEnabled(true);
                btnLogin.setText("登 录");
                if (success) {
                    tvLoginStatus.setText("登录成功");
                    showMain(true);
                    refreshData();
                } else {
                    tvLoginStatus.setText("登录失败: " + error);
                    tvLoginStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    Toast.makeText(MainActivity.this, "登录失败: " + error, Toast.LENGTH_LONG).show();
                    loadCaptcha(); // 刷新验证码
                }
            });
        });
    }

    private void refreshData() {
        String tabidStr = etTabid.getText().toString().trim();
        if (tabidStr.isEmpty()) {
            Toast.makeText(this, "请输入TabID", Toast.LENGTH_SHORT).show();
            return;
        }
        int tabid;
        try {
            tabid = Integer.parseInt(tabidStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "TabID必须为数字", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("加载中…");
        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        swipeRefresh.setRefreshing(true);

        networkHelper.fetchTable(tabid, (headers, rows, error) -> {
            mainHandler.post(() -> {
                swipeRefresh.setRefreshing(false);
                if (error != null) {
                    tvStatus.setText("加载失败: " + error);
                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    Toast.makeText(MainActivity.this, "加载失败: " + error, Toast.LENGTH_LONG).show();
                    return;
                }
                currentHeaders = headers;
                currentRows = rows;
                adapter.setData(headers, rows);
                tvStatus.setText("加载成功，共 " + rows.size() + " 行");
                tvStatus.setTextColor(getResources().getColor(R.color.status_green));
                tvRowCount.setText("行数: " + rows.size());
            });
        });
    }

    private void logout() {
        networkHelper.clearCookies();
        currentHeaders.clear();
        currentRows.clear();
        adapter.setData(currentHeaders, currentRows);
        showMain(false);
        tvLoginStatus.setText("");
        loadCaptcha();
        Toast.makeText(this, "已退出", Toast.LENGTH_SHORT).show();
    }

    private void exportCSV() {
        if (currentRows.isEmpty()) {
            Toast.makeText(this, "没有数据可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        // 构建CSV内容并复制到剪贴板（或保存文件，这里简单复制）
        StringBuilder sb = new StringBuilder();
        sb.append(TextUtils.join(",", currentHeaders)).append("\n");
        for (List<String> row : currentRows) {
            sb.append(TextUtils.join(",", row)).append("\n");
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("data", sb.toString());
        cm.setPrimaryClip(clip);
        Toast.makeText(this, "数据已复制到剪贴板（CSV格式）", Toast.LENGTH_SHORT).show();
    }

    private void showLogsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.logs_dialog, null);
        TextView tvLogs = view.findViewById(R.id.tv_logs);
        Button btnCopy = view.findViewById(R.id.btn_copy_logs);
        tvLogs.setText(Logger.getLogs());
        AlertDialog dialog = builder.setView(view).setTitle("日志").create();
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("logs", Logger.getLogs());
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "日志已复制", Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    // 适配器
    private class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {
        private List<String> headers = new ArrayList<>();
        private List<List<String>> rows = new ArrayList<>();
        private OnItemClickListener listener;

        public void setData(List<String> headers, List<List<String>> rows) {
            this.headers = headers != null ? headers : new ArrayList<>();
            this.rows = rows != null ? rows : new ArrayList<>();
            notifyDataSetChanged();
        }

        public void setOnItemClickListener(OnItemClickListener l) {
            listener = l;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            List<String> row = rows.get(position);
            String first = row.isEmpty() ? "" : row.get(0);
            String second = row.size() > 1 ? row.get(1) : "";
            if (row.size() > 2) {
                second += " ...";
            }
            holder.text1.setText(first);
            holder.text2.setText(second);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onClick(position);
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
            }
        }
    }

    interface OnItemClickListener {
        void onClick(int position);
    }
}
