package com.example.waterapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    private EditText etCust, etUser, etPwd, etCaptcha;
    private ImageView ivCaptcha;
    private Button btnLogin, btnRefreshCaptcha;
    private TextView tvStatus;

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private NetworkUtils networkUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        networkUtils = NetworkUtils.getInstance(this);

        etCust = findViewById(R.id.et_cust);
        etUser = findViewById(R.id.et_user);
        etPwd = findViewById(R.id.et_pwd);
        etCaptcha = findViewById(R.id.et_captcha);
        ivCaptcha = findViewById(R.id.iv_captcha);
        btnLogin = findViewById(R.id.btn_login);
        btnRefreshCaptcha = findViewById(R.id.btn_refresh_captcha);
        tvStatus = findViewById(R.id.tv_status);

        // 自动填充测试数据（可删除）
        etCust.setText("scmz");
        etUser.setText("lwg");
        etPwd.setText("MZzls123");

        btnRefreshCaptcha.setOnClickListener(v -> refreshCaptcha());
        btnLogin.setOnClickListener(v -> doLogin());

        // 尝试自动登录（检查保存的cookies）
        if (networkUtils.hasCookies()) {
            tvStatus.setText("自动登录中...");
            tvStatus.setTextColor(getColor(android.R.color.holo_blue_dark));
            new Thread(() -> {
                boolean ok = networkUtils.testAutoLogin();
                mainHandler.post(() -> {
                    if (ok) {
                        tvStatus.setText("自动登录成功");
                        tvStatus.setTextColor(getColor(R.color.green));
                        startMainActivity();
                    } else {
                        tvStatus.setText("自动登录失败，请手动登录");
                        tvStatus.setTextColor(getColor(R.color.red));
                        networkUtils.clearCookies();
                        refreshCaptcha();
                    }
                });
            }).start();
        } else {
            refreshCaptcha();
        }
    }

    private void refreshCaptcha() {
        new Thread(() -> {
            try {
                byte[] imageBytes = networkUtils.fetchCaptcha();
                if (imageBytes != null) {
                    Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    mainHandler.post(() -> ivCaptcha.setImageBitmap(bmp));
                } else {
                    mainHandler.post(() -> Toast.makeText(LoginActivity.this, "获取验证码失败", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void doLogin() {
        String cust = etCust.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String pwd = etPwd.getText().toString().trim();
        String captcha = etCaptcha.getText().toString().trim();

        if (TextUtils.isEmpty(cust) || TextUtils.isEmpty(user) || TextUtils.isEmpty(pwd) || TextUtils.isEmpty(captcha)) {
            Toast.makeText(this, "请完整填写所有信息", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");
        tvStatus.setText("正在登录...");
        tvStatus.setTextColor(getColor(android.R.color.holo_blue_dark));

        new Thread(() -> {
            boolean success = networkUtils.login(cust, user, pwd, captcha);
            mainHandler.post(() -> {
                btnLogin.setEnabled(true);
                btnLogin.setText(R.string.btn_login);
                if (success) {
                    tvStatus.setText(R.string.status_login_success);
                    tvStatus.setTextColor(getColor(R.color.green));
                    networkUtils.saveCookies();
                    startMainActivity();
                } else {
                    tvStatus.setText(R.string.status_login_failed);
                    tvStatus.setTextColor(getColor(R.color.red));
                    refreshCaptcha();
                }
            });
        }).start();
    }

    private void startMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
