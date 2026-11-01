package com.example.ui;

import androidx.core.content.ContextCompat;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.R;
import com.example.dns.DnsResolverEngine;
import com.example.proxy.KnownProxyApps;
import com.example.proxy.ProxyMessages;
import com.example.proxy.ProxySettings;
import com.example.proxy.core.ProxyEndpoint;
import com.example.proxy.core.ProxyTester;
import com.example.service.ServiceManager;
import com.example.util.AppRouting;
import com.example.util.HelpLinks;
import com.example.util.VpnConflictDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Four plain steps for non-technical users: pick the proxy app, get it ready, test it, switch it on.
 * The switch only turns on after a successful test, so the internet is never cut by a typo.
 */
public class ProxySetupActivity extends AppCompatActivity {

    /** One radio button: a known app, or the "another app" choice (app == null). */
    private static final class Choice {
        final KnownProxyApps.App app;
        final int viewId;

        Choice(KnownProxyApps.App app, int viewId) {
            this.app = app;
            this.viewId = viewId;
        }
    }

    private final List<Choice> choices = new ArrayList<>();
    private RadioGroup rgApps;
    private TextView tvHint;
    private Button btnDocs;
    private TextView tvTestResult;
    private TextView tvState;
    private Button btnOpenApp;
    private Button btnTest;
    private Button btnRestart;
    private ProgressBar pbTest;
    private View llVpnWarning;
    private TextView tvVpnWarning;
    private SwitchCompat swEnabled;
    private EditText etHost;
    private EditText etPort;
    private EditText etUser;
    private EditText etPass;
    private boolean testedOk;
    private boolean changedWhileRunning;
    private boolean suppress;
    private TextView tvAppsSummary;
    private SharedPreferences routingPrefs;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proxy_setup);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        rgApps = findViewById(R.id.rg_proxy_apps);
        tvHint = findViewById(R.id.tv_proxy_hint);
        btnDocs = findViewById(R.id.btn_proxy_docs);
        tvTestResult = findViewById(R.id.tv_test_result);
        tvState = findViewById(R.id.tv_proxy_state);
        btnOpenApp = findViewById(R.id.btn_open_proxy_app);
        btnTest = findViewById(R.id.btn_test_proxy);
        btnRestart = findViewById(R.id.btn_restart_now);
        pbTest = findViewById(R.id.pb_test);
        llVpnWarning = findViewById(R.id.ll_vpn_warning);
        tvVpnWarning = findViewById(R.id.tv_vpn_warning);
        findViewById(R.id.btn_vpn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openVpnSettings();
            }
        });
        swEnabled = findViewById(R.id.sw_proxy_enabled);
        etHost = findViewById(R.id.et_proxy_host);
        etPort = findViewById(R.id.et_proxy_port);
        etUser = findViewById(R.id.et_proxy_user);
        etPass = findViewById(R.id.et_proxy_pass);

        ProxySettings saved = ProxySettings.load(this);
        suppress = true;
        etHost.setText(saved.host);
        etPort.setText(String.valueOf(saved.port));
        etUser.setText(saved.username);
        etPass.setText(saved.password);
        buildChoices(saved);
        swEnabled.setChecked(saved.enabled);
        suppress = false;
        refreshHint();
        updateState();

        TextWatcher invalidate = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!suppress) {
                    markUntested();
                }
            }
        };
        etHost.addTextChangedListener(invalidate);
        etPort.addTextChangedListener(invalidate);
        etUser.addTextChangedListener(invalidate);
        etPass.addTextChangedListener(invalidate);

        rgApps.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (suppress) {
                    return;
                }
                Choice c = selectedChoice();
                if (c != null && c.app != null) {
                    suppress = true;
                    etHost.setText(ProxySettings.DEFAULT_HOST);
                    etPort.setText(String.valueOf(c.app.port));
                    suppress = false;
                }
                refreshHint();
                refreshVpnWarning();
                markUntested();
            }
        });

        final TextView advancedToggle = findViewById(R.id.tv_advanced_toggle);
        final View advanced = findViewById(R.id.ll_advanced);
        advancedToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean show = advanced.getVisibility() != View.VISIBLE;
                advanced.setVisibility(show ? View.VISIBLE : View.GONE);
                advancedToggle.setText(show ? R.string.proxy_advanced_hide : R.string.proxy_advanced_show);
            }
        });

        routingPrefs = getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
        tvAppsSummary = findViewById(R.id.tv_proxy_apps_summary);
        refreshAppsSummary();
        findViewById(R.id.btn_proxy_choose_apps).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppRoutingDialog.show(ProxySetupActivity.this, routingPrefs, backgroundExecutor, AppRouting.Scope.PROXY, new Runnable() {
                    @Override
                    public void run() {
                        refreshAppsSummary();
                        // The routing is read when the VPN starts, so a running tunnel needs a restart.
                        changedWhileRunning = true;
                        updateState();
                    }
                });
            }
        });

        btnOpenApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openProxyApp();
            }
        });
        findViewById(R.id.btn_manage_subscriptions).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ProxySetupActivity.this, VpnServersActivity.class));
            }
        });
        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTest();
            }
        });
        swEnabled.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton button, boolean checked) {
                onSwitchChanged(checked);
            }
        });
        btnRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartProtection();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHint(); // the user may have just installed the proxy app
        refreshVpnWarning(); // ... or just come back from starting it
        updateState();
    }

    // ------------------------------------------------------------------ step 1

    private void buildChoices(ProxySettings saved) {
        rgApps.removeAllViews();
        choices.clear();
        List<KnownProxyApps.App> apps = new ArrayList<>(KnownProxyApps.all());
        // installed apps first
        List<KnownProxyApps.App> ordered = new ArrayList<>();
        for (KnownProxyApps.App a : apps) {
            if (KnownProxyApps.installedPackage(this, a) != null) {
                ordered.add(a);
            }
        }
        for (KnownProxyApps.App a : apps) {
            if (!ordered.contains(a)) {
                ordered.add(a);
            }
        }
        for (KnownProxyApps.App a : ordered) {
            boolean installed = KnownProxyApps.installedPackage(this, a) != null;
            addChoice(a, a.label + " · " + getString(installed ? R.string.proxy_app_installed : R.string.proxy_app_not_installed));
        }
        addChoice(null, getString(R.string.proxy_app_custom));

        boolean configured = getSharedPreferences(ProxySettings.PREFS_NAME, MODE_PRIVATE).contains("port");
        Choice pick = null;
        KnownProxyApps.App savedApp = KnownProxyApps.byPackage(saved.packageName);
        for (Choice c : choices) {
            if (savedApp != null && c.app == savedApp) {
                pick = c;
            }
        }
        if (pick == null) {
            if (configured) {
                pick = choices.get(choices.size() - 1); // saved without a known app: custom
            } else if (!ordered.isEmpty() && KnownProxyApps.installedPackage(this, ordered.get(0)) != null) {
                pick = choices.get(0);
                etPort.setText(String.valueOf(ordered.get(0).port));
            } else {
                pick = choices.get(choices.size() - 1);
            }
        }
        rgApps.check(pick.viewId);
    }

    private void addChoice(KnownProxyApps.App app, String label) {
        RadioButton rb = new RadioButton(this);
        rb.setId(View.generateViewId());
        rb.setText(label);
        rb.setTextColor(getResources().getColor(R.color.text_primary));
        rb.setTextSize(14);
        rgApps.addView(rb);
        choices.add(new Choice(app, rb.getId()));
    }

    private Choice selectedChoice() {
        int id = rgApps.getCheckedRadioButtonId();
        for (Choice c : choices) {
            if (c.viewId == id) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ step 2

    private void refreshHint() {
        Choice c = selectedChoice();
        if (c == null) {
            return;
        }
        // A step-by-step guide for the chosen app (or the wiki for a custom proxy).
        final String docsUrl = c.app != null ? HelpLinks.docsFor(c.app) : HelpLinks.WIKI;
        btnDocs.setText(c.app != null
                ? getString(R.string.proxy_docs_button, c.app.label)
                : getString(R.string.proxy_docs_custom));
        btnDocs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HelpLinks.open(ProxySetupActivity.this, docsUrl != null ? docsUrl : HelpLinks.WIKI);
            }
        });
        if (c.app != null) {
            tvHint.setText(c.app.hintRes);
            if (KnownProxyApps.installedPackage(this, c.app) != null) {
                btnOpenApp.setText(getString(R.string.proxy_open_app, c.app.label));
                btnOpenApp.setVisibility(View.VISIBLE);
            } else {
                btnOpenApp.setVisibility(View.GONE);
            }
        } else {
            tvHint.setText(R.string.proxy_hint_custom);
            btnOpenApp.setVisibility(View.GONE);
        }
    }

    /**
     * Most proxy apps start in their own VPN mode, which blocks NetShield (Android allows one VPN).
     * The connection test still passes in that case, so check for a running VPN separately and say so.
     */
    private void refreshVpnWarning() {
        boolean active = VpnConflictDetector.isOtherVpnActive(this);
        llVpnWarning.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) {
            Choice c = selectedChoice();
            String label = c != null && c.app != null ? c.app.label : getString(R.string.proxy_app_custom_short);
            tvVpnWarning.setText(getString(R.string.proxy_vpn_warning, label));
        }
    }

    private void openVpnSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.proxy_vpn_settings_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void openProxyApp() {
        Choice c = selectedChoice();
        String pkg = c != null && c.app != null ? KnownProxyApps.installedPackage(this, c.app) : null;
        Intent launch = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            Toast.makeText(this, R.string.proxy_open_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(launch);
    }

    // ------------------------------------------------------------------ step 3

    /** Reads and validates the fields; null (with a message) if something is wrong. */
    private ProxySettings readSettings(boolean enabled) {
        String host = etHost.getText().toString().trim();
        if (!ProxySettings.isLoopback(host)) {
            Toast.makeText(this, R.string.proxy_host_invalid, Toast.LENGTH_LONG).show();
            return null;
        }
        int port;
        try {
            port = Integer.parseInt(etPort.getText().toString().trim());
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (port < 1 || port > 65535) {
            Toast.makeText(this, R.string.proxy_port_invalid, Toast.LENGTH_LONG).show();
            return null;
        }
        Choice c = selectedChoice();
        String pkg = null;
        if (c != null && c.app != null) {
            pkg = KnownProxyApps.installedPackage(this, c.app);
            if (pkg == null) {
                pkg = c.app.packages[0];
            }
        }
        return new ProxySettings(enabled, pkg, host, port, etUser.getText().toString(), etPass.getText().toString());
    }

    private void runTest() {
        ProxySettings s = readSettings(swEnabled.isChecked());
        if (s == null) {
            return;
        }
        final ProxyEndpoint endpoint = s.endpoint();
        final Choice c = selectedChoice();
        final String label = c != null && c.app != null ? c.app.label : getString(R.string.proxy_app_custom_short);
        btnTest.setEnabled(false);
        pbTest.setVisibility(View.VISIBLE);
        tvTestResult.setTextColor(getResources().getColor(R.color.text_secondary));
        tvTestResult.setText(R.string.proxy_testing);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ProxyTester.Result result = ProxyTester.test(endpoint, 8000);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        pbTest.setVisibility(View.GONE);
                        btnTest.setEnabled(true);
                        testedOk = result == ProxyTester.Result.OK;
                        String text = getString(ProxyMessages.messageRes(result), label);
                        tvTestResult.setText((testedOk ? "✓ " : "✗ ") + text);
                        tvTestResult.setTextColor(ContextCompat.getColor(ProxySetupActivity.this, testedOk ? R.color.colorAllowed : R.color.colorBlocked));
                        refreshVpnWarning();
                    }
                });
            }
        }, "netshield-proxy-test").start();
    }

    /** Editing anything invalidates the test, and switches the proxy off until it is tested again. */
    private void markUntested() {
        testedOk = false;
        tvTestResult.setText("");
        pbTest.setVisibility(View.GONE);
        if (swEnabled.isChecked()) {
            suppress = true;
            swEnabled.setChecked(false);
            suppress = false;
            ProxySettings.load(this).withEnabled(false).save(this);
            changedWhileRunning = true;
            updateState();
        }
    }

    // ------------------------------------------------------------------ step 4

    private void onSwitchChanged(boolean checked) {
        if (suppress) {
            return;
        }
        if (checked) {
            if (!testedOk) {
                Toast.makeText(this, R.string.proxy_test_first, Toast.LENGTH_SHORT).show();
                revertSwitch(false);
                return;
            }
            ProxySettings s = readSettings(true);
            if (s == null) {
                revertSwitch(false);
                return;
            }
            s.save(this);
        } else {
            ProxySettings.load(this).withEnabled(false).save(this);
        }
        changedWhileRunning = true;
        updateState();
    }

    private void refreshAppsSummary() {
        if (tvAppsSummary != null) {
            tvAppsSummary.setText(AppRouting.summary(this, routingPrefs, AppRouting.Scope.PROXY));
        }
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }

    private void revertSwitch(boolean value) {
        suppress = true;
        swEnabled.setChecked(value);
        suppress = false;
        updateState();
    }

    private void updateState() {
        boolean running = ServiceManager.getCurrentState() == ServiceManager.STATE_VPN;
        if (swEnabled.isChecked()) {
            tvState.setText(running && !changedWhileRunning ? R.string.proxy_state_on_running : R.string.proxy_state_on);
        } else {
            tvState.setText(R.string.proxy_state_off);
        }
        btnRestart.setVisibility(running && changedWhileRunning ? View.VISIBLE : View.GONE);
    }

    private void restartProtection() {
        ServiceManager.stopActiveService(this);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(ProxySetupActivity.this, ConnectionCheckActivity.class));
            }
        }, 800);
        finish();
    }
}
