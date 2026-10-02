package com.example.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.dns.ConnectionVerifier;
import com.example.dns.DnsResolverEngine;
import com.example.service.DnsServerService;
import com.example.service.DnsVpnService;
import com.example.service.ServiceManager;

/**
 * Single entry point for every "start" action (app button, widgets, Quick Settings tile).
 * Shows "Verifying connection", sends a real DNS query through the selected upstream, and only
 * then starts the service. On failure it shows the exact error and offers Google UDP instead.
 * With EXTRA_TOGGLE set (widgets/tile) it stops the service instead if it is already running.
 */
public class ConnectionCheckActivity extends AppCompatActivity {
    public static final String EXTRA_TOGGLE = "com.example.dns.EXTRA_TOGGLE";
    public static final String ISSUES_URL = "https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/issues";
    private static final int REQUEST_VPN = 3001;

    private AlertDialog dialog;
    private volatile boolean cancelled = false;
    private boolean started = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean toggle = getIntent().getBooleanExtra(EXTRA_TOGGLE, false);
        if (toggle && ServiceManager.getCurrentState() != ServiceManager.STATE_STOPPED) {
            ServiceManager.stopActiveService(this);
            ServiceManager.setCurrentState(this, ServiceManager.STATE_STOPPED);
            finish();
            return;
        }
        if (savedInstanceState == null) {
            beginVerification();
        }
    }

    private void beginVerification() {
        showVerifyingDialog();
        final Context appContext = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ConnectionVerifier.Result result = ConnectionVerifier.verifySelected(appContext);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (cancelled || isFinishing() || isDestroyed()) return;
                        dismissDialog();
                        if (result.ok) {
                            startService();
                        } else {
                            showFailureDialog(result);
                        }
                    }
                });
            }
        }, "netshield-verify").start();
    }

    private void showVerifyingDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        Context c = b.getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(24);
        row.setPadding(pad, dp(20), pad, dp(8));
        ProgressBar bar = new ProgressBar(c);
        row.addView(bar, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView tv = new TextView(c);
        tv.setText("Checking that your DNS upstream is reachable\u2026");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(16);
        row.addView(tv, lp);

        dialog = b.setTitle("Verifying connection")
                .setView(row)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        cancelled = true;
                        finish();
                    }
                })
                .create();
        dialog.show();
    }

    private void showFailureDialog(final ConnectionVerifier.Result result) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        Context c = b.getContext();

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        col.setPadding(pad, dp(12), pad, 0);

        TextView intro = new TextView(c);
        intro.setText("NetShield couldn't connect using " + result.method
                + ". If this keeps happening, please report it at:");
        intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        col.addView(intro);

        TextView link = new TextView(c);
        link.setText(ISSUES_URL);
        link.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        link.setAutoLinkMask(Linkify.WEB_URLS);
        Linkify.addLinks(link, Linkify.WEB_URLS);
        LinearLayout.LayoutParams linkLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        linkLp.topMargin = dp(4);
        col.addView(link, linkLp);

        // Copy block: selectable monospace error + one-tap copy.
        TextView copy = new TextView(c);
        copy.setText("Copy error");
        copy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        copy.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.setPadding(0, dp(12), 0, dp(4));
        copy.setGravity(Gravity.END);
        copy.setClickable(true);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("NetShield error", result.error));
                    Toast.makeText(ConnectionCheckActivity.this, "Error copied", Toast.LENGTH_SHORT).show();
                }
            }
        });
        col.addView(copy, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView err = new TextView(c);
        err.setText(result.error.trim());
        err.setTypeface(Typeface.MONOSPACE);
        err.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        err.setTextIsSelectable(true);
        err.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x22888888);
        bg.setCornerRadius(dp(8));
        err.setBackground(bg);
        ScrollView scroll = new ScrollView(c);
        scroll.addView(err);
        col.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(150)));

        TextView ask = new TextView(c);
        ask.setText("Would you like to connect using Google UDP (8.8.8.8) instead? "
                + "This switches your upstream setting to plain UDP.");
        ask.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        ask.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams askLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        askLp.topMargin = dp(14);
        col.addView(ask, askLp);

        dialog = b.setTitle("Connection failed")
                .setView(col)
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        SharedPreferences prefs = getSharedPreferences(DnsResolverEngine.PREFS_NAME, MODE_PRIVATE);
                        prefs.edit()
                                .putString("upstream_mode", "UDP")
                                .putString("upstream_primary", "8.8.8.8")
                                .putString("upstream_secondary", "8.8.4.4")
                                .apply();
                        startService();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        finish();
                    }
                })
                .create();
        dialog.show();
    }

    /** Same start logic the app/widgets/tile used before, now reached only after a successful check. */
    private void startService() {
        if (started) return;
        SharedPreferences prefs = getSharedPreferences(DnsResolverEngine.PREFS_NAME, MODE_PRIVATE);
        String mode = prefs.getString("operation_mode", "VPN");
        if ("VPN".equalsIgnoreCase(mode)) {
            Intent vpnPrepare = VpnService.prepare(this);
            if (vpnPrepare != null) {
                startActivityForResult(vpnPrepare, REQUEST_VPN);
                return;
            }
            launch(new Intent(this, DnsVpnService.class), ServiceManager.STATE_VPN);
        } else {
            launch(new Intent(this, DnsServerService.class), ServiceManager.STATE_SERVER);
        }
        finish();
    }

    private void launch(Intent intent, int state) {
        started = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        ServiceManager.setCurrentState(this, state);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) {
                launch(new Intent(this, DnsVpnService.class), ServiceManager.STATE_VPN);
            }
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        dismissDialog();
        super.onDestroy();
    }

    private void dismissDialog() {
        if (dialog != null && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
