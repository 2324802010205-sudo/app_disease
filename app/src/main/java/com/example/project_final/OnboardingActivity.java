package com.example.project_final;

import android.content.Intent;

public class OnboardingActivity extends BaseActivity {
    @Override
    protected int layout() {
        return R.layout.activity_onboarding;
    }

    @Override
    protected boolean padTopInset() {
        return false;
    }

    @Override
    protected boolean requiresLogin() {
        return false;
    }

    @Override
    protected void onReady() {
        findViewById(R.id.btn_skip).setOnClickListener(v -> openLogin());
        findViewById(R.id.btn_next).setOnClickListener(v -> openLogin());
    }

    private void openLogin() {
        SplashActivity.markOnboarded(this);
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
