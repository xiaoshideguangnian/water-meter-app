package com.water.app.ui;

import android.content.Intent;
import android.graphics.Bitmap;
android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.water.app.R;
import com.water.app.network.WaterApi;
import com.water.app.util.LogUtil;

public class LoginActivity extends AppCompatActivity {
    private EditText etCust, etUser, etPwd, etCaptcha;
    private ImageView ivCaptcha;
    private Button btnLogin, btnRefreshCaptcha;
    private TextView tvStatus;
    private WaterApi api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        api = new WaterApi(this);
        etCust = findViewById(R.id.et_cust);
        etUser = findViewById(R.id.et_user);
        etPwd = findViewById(R.id.et_pwd);
        etCaptcha = findViewById(R.id.et_captcha);
        ivCaptcha = findViewById(R.id.iv_captcha);
        btnLogin = findViewById(R.id.btn_login);
        btnRefreshCaptcha = findViewById(R.id.btn_refresh_captcha);
        tvStatus = findViewById(R.id.tv_status);

        // 尝试自动登录
        new CheckLoginTask().execute();

        loadCaptcha();

        btnRefreshCaptcha.setOnClickListener(v -> loadCaptcha());
        btnLogin.setOnClickListener(v -> doLogin());
    }

    private void loadCaptcha() {
        new AsyncTask<Void, Void, byte[]>() {
            @Override
            protected byte[] doInBackground(Void... voids) {
                try {
                    return api.getCaptchaImage();
                } catch (Exception e) {
                    LogUtil.add("获取验证码失败: " + e.getMessage());
                    return null;
                }
            }
            @Override
            protected void onPostExecute(byte[] bytes) {
                if (bytes != null) {
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    ivCaptcha.setImageBitmap(bmp);
                } else {
                    Toast.makeText(LoginActivity.this, "获取验证码失败", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void doLogin() {
        String cust = etCust.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String pwd = etPwd.getText().toString().trim();
        String captcha = etCaptcha.getText().toString().trim();
        if (cust.isEmpty() || user.isEmpty() || pwd.isEmpty() || captcha.isEmpty()) {
            Toast.makeText(this, "请完整填写信息", Toast.LENGTH_SHORT).show();
            return;
        }
        btnLogin.setEnabled(false);
        tvStatus.setText("登录中...");
        new AsyncTask<Void, Void, WaterApi.LoginResult>() {
            @Override
            protected WaterApi.LoginResult doInBackground(Void... voids) {
                try {
                    return api.login(cust, user, pwd, captcha);
                } catch (Exception e) {
                    LogUtil.add("登录异常: " + e.getMessage());
                    return new WaterApi.LoginResult(false, "网络错误: " + e.getMessage());
                }
            }
            @Override
            protected void onPostExecute(WaterApi.LoginResult result) {
                btnLogin.setEnabled(true);
                if (result.success) {
                    LogUtil.add("登录成功");
                    tvStatus.setText("登录成功");
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else {
                    LogUtil.add("登录失败: " + result.message);
                    tvStatus.setText("登录失败: " + result.message);
                    Toast.makeText(LoginActivity.this, result.message, Toast.LENGTH_LONG).show();
                    loadCaptcha();
                }
            }
        }.execute();
    }

    private class CheckLoginTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            return api.checkLogin();
        }
        @Override
        protected void onPostExecute(Boolean loggedIn) {
            if (loggedIn) {
                LogUtil.add("自动登录成功");
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            } else {
                LogUtil.add("未登录或登录过期");
                tvStatus.setText("请登录");
            }
        }
    }
}
