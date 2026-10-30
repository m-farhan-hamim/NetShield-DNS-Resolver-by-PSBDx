package com.example.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.example.BuildConfig;
import com.example.R;
import com.example.util.RemoteSyncScheduler;

import java.util.concurrent.ExecutorService;

/**
 * The "What's new" pop-up shown once to people who UPDATED the app (new installs get the setup
 * wizard instead and are marked as having seen it). It lists what changed in this version, calls out
 * the remote list sync that was easy to miss, and offers the skippable feature tour.
 */
public final class WhatsNew {

    /** The versionCode whose "What's new" the user has already seen. */
    public static final String KEY_SEEN_CODE = "whats_new_seen_code";

    private WhatsNew() {
    }

    /** Marks this version as seen, for example after a fresh install that went through the wizard. */
    public static void markSeen(SharedPreferences prefs) {
        prefs.edit().putInt(KEY_SEEN_CODE, BuildConfig.VERSION_CODE).apply();
    }

    /** @return true when the pop-up was shown, so the caller can hold back other dialogs this launch. */
    public static boolean maybeShow(Activity activity, SharedPreferences prefs, ExecutorService background) {
        // Only after the first-run decision: a fresh install is still in (or skipped) the wizard.
        if (!prefs.getBoolean(SetupWizardActivity.KEY_DONE, false)) {
            return false;
        }
        if (prefs.getInt(KEY_SEEN_CODE, 0) >= BuildConfig.VERSION_CODE) {
            return false;
        }
        show(activity, prefs, background);
        return true;
    }

    public static void show(final Activity activity, final SharedPreferences prefs, final ExecutorService background) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_whats_new, null);
        LinearLayout list = view.findViewById(R.id.ll_whats_new_items);
        int pad = (int) (6 * activity.getResources().getDisplayMetrics().density);
        for (String item : activity.getResources().getStringArray(R.array.whats_new_items)) {
            TextView row = new TextView(activity);
            row.setText("\u2022  " + item);
            row.setTextColor(activity.getColor(R.color.text_primary));
            row.setTextSize(14);
            row.setPadding(0, pad, 0, 0);
            list.addView(row);
        }
        SwitchCompat sync = view.findViewById(R.id.sw_whats_new_sync);
        sync.setChecked(RemoteSyncScheduler.isEnabled(activity));
        sync.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                RemoteSyncScheduler.setEnabled(activity, isChecked);
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.whats_new_title, BuildConfig.VERSION_NAME))
                .setView(view)
                .setPositiveButton(R.string.whats_new_tour, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        FeatureTour.show(activity, prefs, background);
                    }
                })
                .setNegativeButton(R.string.whats_new_close, null)
                .create();
        // Seen as soon as it is on screen, however it is closed, so it never nags twice.
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                markSeen(prefs);
            }
        });
        dialog.show();
    }
}
