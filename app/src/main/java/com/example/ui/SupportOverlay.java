package com.example.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.example.R;
import com.example.util.HelpLinks;

/**
 * The "Need support" overlay: the PSBDx forum (suggested), the GitHub issue tracker and the app wiki. Opened from Settings > About and from the feature tour.
 */
public final class SupportOverlay {

    private SupportOverlay() {
    }

    public static void show(final Activity activity) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_support, null);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.support_title)
                .setView(view)
                .setNegativeButton(R.string.support_close, null)
                .create();
        bind(view, R.id.btn_support_forum, activity, dialog, HelpLinks.FORUM);
        bind(view, R.id.btn_support_github, activity, dialog, HelpLinks.GITHUB_ISSUES);
        bind(view, R.id.btn_support_wiki, activity, dialog, HelpLinks.WIKI);
        dialog.show();
    }

    private static void bind(View root, int id, final Activity activity, final AlertDialog dialog, final String url) {
        root.findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HelpLinks.open(activity, url);
                dialog.dismiss();
            }
        });
    }
}
