package com.example.ui;

import android.app.Activity;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.R;

/**
 * Lets the user pick the app language without touching the phone language. On Android 13+ the same
 * choice is also in the system's per-app language settings; AppCompat keeps both in sync and, on older
 * versions, remembers the choice itself (AppLocalesMetadataHolderService in the manifest).
 */
public final class LanguagePicker {

    private static final String[] TAGS = {"", "en", "bn", "hi"};

    private LanguagePicker() {
    }

    public static void show(final Activity activity) {
        final CharSequence[] names = {
                activity.getString(R.string.language_system),
                activity.getString(R.string.language_english),
                activity.getString(R.string.language_bengali),
                activity.getString(R.string.language_hindi)};

        int checked = 0;
        LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
        if (!current.isEmpty() && current.get(0) != null) {
            String language = current.get(0).getLanguage();
            for (int i = 1; i < TAGS.length; i++) {
                if (TAGS[i].equals(language)) {
                    checked = i;
                }
            }
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // An empty list means "follow the phone". The activity is recreated by AppCompat.
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(TAGS[which]));
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
