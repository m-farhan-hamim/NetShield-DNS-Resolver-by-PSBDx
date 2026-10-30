package com.example.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.R;

/**
 * Android 17 (API 37) and newer are newer than this build was written and tested for. On those
 * versions the system may put the app to sleep or restrict its background work, which is what users
 * reported as high CPU use and protection stopping. The fix we can offer is to tell the user, and to
 * suggest turning battery optimization off for NetShield (or setting its battery usage to Unrestricted).
 */
public final class BatteryOptimizationHelper {

    /** Highest Android API level this build supports: 36 is Android 16, the targetSdk. */
    public static final int MAX_SUPPORTED_SDK = 36;

    private static final String KEY_ACK_SDK = "android_unsupported_warning_ack_sdk";

    /** The reminder is shown at most once per app process, so it never nags on every screen change. */
    private static boolean shownThisProcess = false;

    private BatteryOptimizationHelper() {
    }

    /** The setup wizard already showed the same advice, so do not repeat it in a dialog right after. */
    public static void suppressForThisSession() {
        shownThisProcess = true;
    }

    public static boolean isUnsupportedAndroid() {
        return Build.VERSION.SDK_INT > MAX_SUPPORTED_SDK;
    }

    /** True when the app is exempt from battery optimization ("Don't optimize" / "Unrestricted"). */
    public static boolean isUnrestricted(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /** Shows the warning on an unsupported Android version, unless it would be pointless or unwanted. */
    public static void maybeShowWarning(Activity activity, SharedPreferences prefs) {
        if (shownThisProcess || !isUnsupportedAndroid() || isUnrestricted(activity)) {
            return;
        }
        if (prefs.getInt(KEY_ACK_SDK, 0) == Build.VERSION.SDK_INT) {
            return; // the user said "don't show again" for this Android version
        }
        shownThisProcess = true;
        showWarning(activity, prefs);
    }

    public static void showWarning(final Activity activity, final SharedPreferences prefs) {
        final int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView body = new TextView(activity);
        body.setText(activity.getString(R.string.android_unsupported_body, Build.VERSION.RELEASE));
        body.setTextColor(activity.getColor(R.color.text_primary));
        body.setTextSize(14);
        box.addView(body);

        TextView howTo = new TextView(activity);
        howTo.setText(R.string.android_unsupported_howto);
        howTo.setTextColor(activity.getColor(R.color.text_secondary));
        howTo.setTextSize(13);
        howTo.setPadding(0, pad / 2, 0, 0);
        box.addView(howTo);

        final CheckBox dontShow = new CheckBox(activity);
        dontShow.setText(R.string.battery_dont_show);
        dontShow.setTextColor(activity.getColor(R.color.text_secondary));
        dontShow.setTextSize(12);
        box.addView(dontShow);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.android_unsupported_title, Build.VERSION.RELEASE))
                .setView(box)
                .setPositiveButton(R.string.battery_open_app_info, null)
                .setNeutralButton(R.string.battery_open_list, null)
                .setNegativeButton(R.string.battery_later, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        rememberChoice(prefs, dontShow.isChecked());
                    }
                });
        final AlertDialog dialog = builder.create();
        // The two settings buttons open another screen, so the dialog must stay put behind it.
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rememberChoice(prefs, dontShow.isChecked());
                        openAppBatterySettings(activity);
                        dialog.dismiss();
                    }
                });
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rememberChoice(prefs, dontShow.isChecked());
                        openOptimizationList(activity);
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
    }

    private static void rememberChoice(SharedPreferences prefs, boolean dontShowAgain) {
        if (dontShowAgain) {
            prefs.edit().putInt(KEY_ACK_SDK, Build.VERSION.SDK_INT).apply();
        }
    }

    /** NetShield's own App info page, which has the Battery entry with the "Unrestricted" choice. */
    public static void openAppBatterySettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, R.string.battery_settings_error, Toast.LENGTH_LONG).show();
        }
    }

    /** The system list of apps with battery optimization on or off. Needs no extra permission. */
    public static void openOptimizationList(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            openAppBatterySettings(activity);
        }
    }
}
