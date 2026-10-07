package com.example.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Toast;

import com.example.R;
import com.example.dns.DnsResolverEngine;

/** Explains how to run NetShield next to another VPN/proxy app such as v2rayNG. */
public final class CoexistenceGuide {
    private CoexistenceGuide() {
    }

    /** @param onClosed optional, run after the guide is dismissed (may be null) */
    public static void show(final Activity activity, final Runnable onClosed) {
        SharedPreferences prefs =
                activity.getSharedPreferences(DnsResolverEngine.PREFS_NAME, Context.MODE_PRIVATE);
        final String address = "127.0.0.1:" + prefs.getInt("server_port", 5353);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.coexist_guide_title)
                .setMessage(activity.getString(R.string.coexist_guide_body, address))
                .setPositiveButton(R.string.coexist_guide_copy, null)
                .setNegativeButton(R.string.coexist_guide_close, null)
                .create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                if (onClosed != null) {
                    onClosed.run();
                }
            }
        });
        dialog.show();
        // Overridden after show() so copying keeps the guide open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard =
                        (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Local DNS Server", address));
                Toast.makeText(activity,
                        activity.getString(R.string.coexist_address_copied, address),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
