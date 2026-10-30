package com.example.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.dns.DnsResolverEngine;
import com.example.util.BatteryOptimizationHelper;
import com.example.util.RemoteSyncScheduler;

/**
 * The setup wizard shown once, on the first launch after a fresh install. Every step is optional and
 * nothing here is required to use the app: it only explains how protection works and gets the three
 * things that most often go wrong out of the way (VPN permission, notifications, battery optimization).
 */
public class SetupWizardActivity extends AppCompatActivity {

    /** Marks the wizard as finished or skipped, so it is never shown again on its own. */
    public static final String KEY_DONE = "setup_wizard_done";
    /** Set when the wizard is opened again from Settings: finishing then just closes it. */
    public static final String EXTRA_RERUN = "rerun";

    private static final int STEP_COUNT = 7;
    private static final int STEP_BATTERY = 4;
    /** An update keeps firstInstallTime but moves lastUpdateTime; allow a little clock slack. */
    private static final long UPDATE_THRESHOLD_MS = 60_000L;

    private SharedPreferences prefs;
    private boolean rerun;
    private int step = 0;

    private View[] panels;
    private TextView tvStep;
    private ProgressBar pbWizard;
    private Button btnBack;
    private Button btnNext;
    private TextView tvVpnState;
    private TextView tvNotifState;
    private Button btnNotif;
    private TextView tvBatteryState;
    private TextView tvBatteryUnsupported;
    private SwitchCompat swSync;

    private ActivityResultLauncher<Intent> vpnLauncher;
    private ActivityResultLauncher<String> notificationLauncher;

    /**
     * True only on the first run after a fresh install. An app that was merely updated (the user
     * already knows the app, and has their own settings) is marked done silently and never sees it.
     */
    public static boolean shouldShow(Context context) {
        SharedPreferences p = context.getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
        if (p.getBoolean(KEY_DONE, false)) {
            return false;
        }
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (info.lastUpdateTime - info.firstInstallTime > UPDATE_THRESHOLD_MS) {
                p.edit().putBoolean(KEY_DONE, true).apply();
                return false;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // cannot tell: show it, it is harmless
        }
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wizard);

        prefs = getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
        rerun = getIntent().getBooleanExtra(EXTRA_RERUN, false);
        if (savedInstanceState != null) {
            step = Math.max(0, Math.min(STEP_COUNT - 1, savedInstanceState.getInt("step", 0)));
        }

        panels = new View[]{
                findViewById(R.id.panel_0), findViewById(R.id.panel_1), findViewById(R.id.panel_2),
                findViewById(R.id.panel_3), findViewById(R.id.panel_4), findViewById(R.id.panel_5),
                findViewById(R.id.panel_6)};
        tvStep = findViewById(R.id.tv_wizard_step);
        pbWizard = findViewById(R.id.pb_wizard);
        pbWizard.setMax(STEP_COUNT);
        btnBack = findViewById(R.id.btn_wizard_back);
        btnNext = findViewById(R.id.btn_wizard_next);
        tvVpnState = findViewById(R.id.tv_wizard_vpn_state);
        tvNotifState = findViewById(R.id.tv_wizard_notif_state);
        btnNotif = findViewById(R.id.btn_wizard_notif);
        tvBatteryState = findViewById(R.id.tv_wizard_battery_state);
        tvBatteryUnsupported = findViewById(R.id.tv_wizard_battery_unsupported);

        vpnLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new androidx.activity.result.ActivityResultCallback<androidx.activity.result.ActivityResult>() {
                    @Override
                    public void onActivityResult(androidx.activity.result.ActivityResult result) {
                        refreshVpnState();
                    }
                });
        notificationLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                new androidx.activity.result.ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean granted) {
                        refreshNotificationState();
                    }
                });

        findViewById(R.id.tv_wizard_skip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishWizard();
            }
        });
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goBack();
            }
        });
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (step >= STEP_COUNT - 1) {
                    finishWizard();
                } else {
                    showStep(step + 1);
                }
            }
        });
        findViewById(R.id.btn_wizard_vpn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestVpnPermission();
            }
        });
        btnNotif.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        });
        findViewById(R.id.btn_wizard_battery_app).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BatteryOptimizationHelper.openAppBatterySettings(SetupWizardActivity.this);
            }
        });
        findViewById(R.id.btn_wizard_battery_list).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BatteryOptimizationHelper.openOptimizationList(SetupWizardActivity.this);
            }
        });

        // Remote list sync step: on = sync now and then every 24 hours, with a notification each time.
        swSync = findViewById(R.id.sw_wizard_sync);
        swSync.setChecked(RemoteSyncScheduler.isEnabled(this));
        swSync.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                RemoteSyncScheduler.setEnabled(SetupWizardActivity.this, isChecked);
                if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && ContextCompat.checkSelfPermission(SetupWizardActivity.this,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // The sync still runs without it, but the "finished" notification needs this.
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        });

        final RadioGroup rgDns = findViewById(R.id.rg_wizard_dns);
        rgDns.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                applyDnsChoice(checkedId);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });

        showStep(step);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have just come back from a system settings screen.
        refreshVpnState();
        refreshNotificationState();
        refreshBatteryState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("step", step);
    }

    private void goBack() {
        if (step > 0) {
            showStep(step - 1);
        } else {
            finish();
        }
    }

    private void showStep(int newStep) {
        step = newStep;
        for (int i = 0; i < panels.length; i++) {
            panels[i].setVisibility(i == step ? View.VISIBLE : View.GONE);
        }
        tvStep.setText(getString(R.string.wizard_step_format, step + 1, STEP_COUNT));
        pbWizard.setProgress(step + 1);
        btnBack.setVisibility(step == 0 ? View.INVISIBLE : View.VISIBLE);
        btnNext.setText(step == STEP_COUNT - 1 ? R.string.wizard_finish : R.string.wizard_next);
        if (step == STEP_BATTERY) {
            refreshBatteryState();
            BatteryOptimizationHelper.suppressForThisSession();
        }
    }

    private void requestVpnPermission() {
        Intent consent = VpnService.prepare(this);
        if (consent == null) {
            refreshVpnState(); // already allowed
        } else {
            vpnLauncher.launch(consent);
        }
    }

    private void refreshVpnState() {
        boolean allowed = VpnService.prepare(this) == null;
        tvVpnState.setText(allowed ? R.string.wizard_vpn_granted : R.string.wizard_vpn_denied);
    }

    private void refreshNotificationState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            btnNotif.setVisibility(View.GONE);
            tvNotifState.setText(R.string.wizard_notif_not_needed);
            return;
        }
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        btnNotif.setVisibility(granted ? View.GONE : View.VISIBLE);
        if (granted) {
            tvNotifState.setText(R.string.wizard_notif_granted);
        } else {
            tvNotifState.setText("");
        }
    }

    private void refreshBatteryState() {
        boolean unrestricted = BatteryOptimizationHelper.isUnrestricted(this);
        tvBatteryState.setText(unrestricted ? R.string.wizard_battery_state_ok : R.string.wizard_battery_state_restricted);
        if (BatteryOptimizationHelper.isUnsupportedAndroid()) {
            tvBatteryUnsupported.setVisibility(View.VISIBLE);
            tvBatteryUnsupported.setText(getString(R.string.android_unsupported_body, Build.VERSION.RELEASE));
        } else {
            tvBatteryUnsupported.setVisibility(View.GONE);
        }
    }

    /** Saves the chosen provider the same way the DNS presets in Settings do. */
    private void applyDnsChoice(int checkedId) {
        String doh;
        String dot;
        String primary;
        String secondary;
        if (checkedId == R.id.rb_wizard_google) {
            doh = "https://dns.google/dns-query";
            dot = "dns.google";
            primary = "8.8.8.8"; // NOSONAR public DNS resolver address
            secondary = "8.8.4.4"; // NOSONAR public DNS resolver address
        } else if (checkedId == R.id.rb_wizard_quad9) {
            doh = "https://dns.quad9.net/dns-query";
            dot = "dns.quad9.net";
            primary = "9.9.9.9"; // NOSONAR public DNS resolver address
            secondary = "149.112.112.112"; // NOSONAR public DNS resolver address
        } else if (checkedId == R.id.rb_wizard_adguard) {
            doh = "https://dns.adguard-dns.com/dns-query";
            dot = "dns.adguard-dns.com";
            primary = "94.140.14.14"; // NOSONAR public DNS resolver address
            secondary = "94.140.15.15"; // NOSONAR public DNS resolver address
        } else {
            doh = "https://cloudflare-dns.com/dns-query";
            dot = "cloudflare-dns.com";
            primary = "1.1.1.1"; // NOSONAR public DNS resolver address
            secondary = "1.0.0.1"; // NOSONAR public DNS resolver address
        }
        prefs.edit()
                .putString("doh_url", doh)
                .putString("dot_host", dot)
                .putString("upstream_primary", primary)
                .putString("upstream_secondary", secondary)
                .apply();
    }

    private void finishWizard() {
        prefs.edit().putBoolean(KEY_DONE, true).apply();
        WhatsNew.markSeen(prefs); // a fresh install has nothing "new" to catch up on
        if (!rerun) {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }
}
