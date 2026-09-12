package com.example.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.R;

public class SplashActivity extends AppCompatActivity {
    private static final long NAV_DELAY_MS = 2200L;
    private static final long PULSE_START_DELAY_MS = 500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable navigateRunnable;
    private ObjectAnimator pulseAnimator;
    private boolean navigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        final ImageView logo = findViewById(R.id.iv_splash_logo);
        final TextView appName = findViewById(R.id.tv_splash_app_name);
        final TextView tagline = findViewById(R.id.tv_splash_tagline);
        final TextView poweredBy = findViewById(R.id.tv_powered_by);
        final View pulseRing = findViewById(R.id.view_pulse_ring);

        runEntranceAnimation(logo, appName, tagline, poweredBy, pulseRing);

        // Tap anywhere to skip straight to the app.
        findViewById(android.R.id.content).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToMain();
            }
        });

        navigateRunnable = new Runnable() {
            @Override
            public void run() {
                goToMain();
            }
        };
        handler.postDelayed(navigateRunnable, NAV_DELAY_MS);
    }

    private void runEntranceAnimation(ImageView logo, TextView appName, TextView tagline,
                                       TextView poweredBy, final View pulseRing) {
        // Logo: fade in while popping up to full size with a slight overshoot.
        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.4f, 1f);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.4f, 1f);
        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(logoAlpha, logoScaleX, logoScaleY);
        logoSet.setDuration(650);
        logoSet.setInterpolator(new OvershootInterpolator(2.2f));

        // App name: fades in while rising slightly, just after the logo pops in.
        appName.setTranslationY(24f);
        ObjectAnimator nameAlpha = ObjectAnimator.ofFloat(appName, View.ALPHA, 0f, 1f);
        ObjectAnimator nameTranslate = ObjectAnimator.ofFloat(appName, View.TRANSLATION_Y, 24f, 0f);
        AnimatorSet nameSet = new AnimatorSet();
        nameSet.playTogether(nameAlpha, nameTranslate);
        nameSet.setDuration(450);
        nameSet.setStartDelay(350);
        nameSet.setInterpolator(new AccelerateDecelerateInterpolator());

        // Tagline fades in next.
        ObjectAnimator taglineAlpha = ObjectAnimator.ofFloat(tagline, View.ALPHA, 0f, 1f);
        taglineAlpha.setDuration(450);
        taglineAlpha.setStartDelay(550);

        // "Powered by PSBDx" fades in last.
        ObjectAnimator poweredAlpha = ObjectAnimator.ofFloat(poweredBy, View.ALPHA, 0f, 1f);
        poweredAlpha.setDuration(500);
        poweredAlpha.setStartDelay(850);

        AnimatorSet entrance = new AnimatorSet();
        entrance.playTogether(logoSet, nameSet, taglineAlpha, poweredAlpha);
        entrance.start();

        // Looping "breathing" ring behind the logo, kicked off once the logo has popped in.
        pulseRing.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || navigated) return;
                pulseRing.setPivotX(pulseRing.getWidth() / 2f);
                pulseRing.setPivotY(pulseRing.getHeight() / 2f);
                PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.35f);
                PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.35f);
                PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 0f);
                pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(pulseRing, scaleX, scaleY, alpha);
                pulseAnimator.setDuration(1400);
                pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                pulseAnimator.start();
            }
        }, PULSE_START_DELAY_MS);
    }

    private void goToMain() {
        if (navigated) return;
        navigated = true;
        handler.removeCallbacks(navigateRunnable);
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        super.onDestroy();
    }
}
