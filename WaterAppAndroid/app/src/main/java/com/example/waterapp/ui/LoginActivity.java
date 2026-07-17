package com.example.waterapp.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.List;
import okhttp3.*;
import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor;
import com.example.waterapp.R;
import com.example.waterapp.network.ApiClient;
import com.example.waterapp.utils.L;

public class LoginActivity extends AppCompatActivity {
    private EditText etCustCode, etUserCode, etPassword, etCaptcha;
    private ImageView ivCaptcha;
    private Button btnLogin;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        initViews();
        ApiClient.init(getApplicationContext());
        loadCaptcha();
        tryAutoLogin();
    }

    private void initViews() {
        etCustCode = findViewById(R.id.et_cust_code);
        etUserCode = findViewById(R.id.et_user_code);
        etPassword = findViewById(R.id.et_password);
        etCaptcha = findViewById(R.id.et_captcha);
        ivCaptcha = findViewById(R.id.iv_captcha);
        btnLogin = findViewById(R.id.btn_login);
        tvStatus = findViewById(R.id.tv_status);

        ivCaptcha.setOnClickListener(v -> loadCaptcha());
        btnLogin.setOnClickListener(v -> doLogin());
    }

    private void loadCaptcha() {
        new Thread(() -> {
            try {
                byte[] bytes = ApiClient.getCaptchaBytes();
                if (bytes != null) {
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    runOnUiThread(() -> ivCaptcha.setImageBitmap(bmp));
                }
            } catch (Exception e) {
                L.e("Load captcha error", e);
            }
        }).start();
    }

    private void tryAutoLogin() {
        if (ApiClient.hasCookie()) {
            new Thread(() -> {
                try {
                    // Verify by fetching a simple page or known TabID
                    boolean valid = ApiClient.verifySession(2172);
                    if (valid) {
                        runOnUiThread(() -> {
                            tvStatus.setText("自动登录成功");
                            startMainActivity();
                        });
                        return;
                    }
                } catch (Exception e) {
                    L.e("Auto login fail", e);
                }
                runOnUiThread(() -> {
                    ApiClient.clearCookies();
                    tvStatus.setText("登录已过期，请重新登录");
                });
            }).start();
        }
    }

    private void doLogin() {
        String cust = etCustCode.getText().toString().trim();
        String user = etUserCode.getText().toString().trim();
        String pwd = etPassword.getText().toString().trim();
        String captcha = etCaptcha.getText().toString().trim();

        if (TextUtils.isEmpty(cust) || TextUtils.isEmpty(user) || TextUtils.isEmpty(pwd) || TextUtils.isEmpty(captcha)) {
            tvStatus.setText("请填写完整信息");
            return;
        }

        btnLogin.setEnabled(false);
        tvStatus.setText("登录中...");

        new Thread(() -> {
            try {
                boolean success = ApiClient.login(cust, user, pwd, captcha);
                runOnUiThread(() -> {
                    if (success) {
                        tvStatus.setText("登录成功");
                        ApiClient.saveCookies();
                        startMainActivity();
                    } else {
                        tvStatus.setText("登录失败，请检查输入");
                        btnLogin.setEnabled(true);
                        loadCaptcha();
                    }
                });
            } catch (Exception e) {
                L.e("Login error", e);
                runOnUiThread(() -> {
                    tvStatus.setText("网络错误: " + e.getMessage());
                    btnLogin.setEnabled(true);
                    loadCaptcha();
                });
            }
        }).start();
    }

    private void startMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
