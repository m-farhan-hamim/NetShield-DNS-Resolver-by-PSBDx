package com.example.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.R;

/** Explains why DoH/DoT are temporarily unavailable and that NetShield uses plain UDP for now. */
final class EncryptedDnsNotice {

    private EncryptedDnsNotice() {
    }

    static void show(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        Context c = b.getContext();
        float d = activity.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(d, 24), dp(d, 24), dp(d, 24), dp(d, 8));

        // Badge
        TextView badge = new TextView(c);
        badge.setText("\uD83D\uDEE1\uFE0F");
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(0x2200E676);
        badgeBg.setStroke(dp(d, 2), ContextCompat.getColor(c, R.color.colorAllowed));
        badge.setBackground(badgeBg);
        root.addView(badge, new LinearLayout.LayoutParams(dp(d, 76), dp(d, 76)));

        // Title
        TextView title = new TextView(c);
        title.setText("DoH & DoT are paused");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = wrap();
        tlp.topMargin = dp(d, 14);
        root.addView(title, tlp);

        // Status pills
        LinearLayout pills = new LinearLayout(c);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.CENTER);
        pills.addView(pill(c, d, "DoH  \u2715", ContextCompat.getColor(c, R.color.colorBlocked)));
        pills.addView(pill(c, d, "DoT  \u2715", ContextCompat.getColor(c, R.color.colorBlocked)));
        pills.addView(pill(c, d, "UDP  \u2713", ContextCompat.getColor(c, R.color.colorAllowed)));
        LinearLayout.LayoutParams plp = wrap();
        plp.topMargin = dp(d, 12);
        root.addView(pills, plp);

        // Message card
        TextView msg = new TextView(c);
        msg.setText("DoH and DoT are switched off for now while I fix connection and security "
                + "problems with them. NetShield is using standard UDP DNS instead.\n\n"
                + "UDP DNS is not encrypted, so your network or ISP can see which domains you look up. "
                + "Blocking still works as usual.\n\n"
                + "I'm continuously working on a fix, but with my busy schedule I don't want to ship "
                + "encrypted DNS in a state that could put your privacy at risk. "
                + "I'll turn it back on as soon as I'm free from work.\n\n"
                + "\u2014 M. Farhan Hamim, developer");
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        msg.setLineSpacing(0f, 1.15f);
        msg.setPadding(dp(d, 16), dp(d, 14), dp(d, 16), dp(d, 14));
        GradientDrawable card = new GradientDrawable();
        card.setColor(0x18888888);
        card.setCornerRadius(dp(d, 14));
        msg.setBackground(card);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(d, 16);
        root.addView(msg, mlp);

        b.setView(root).setPositiveButton("Got it", null).show();
    }

    private static TextView pill(Context c, float d, String text, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(color);
        t.setPadding(dp(d, 12), dp(d, 5), dp(d, 12), dp(d, 5));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(d, 20));
        bg.setColor((color & 0x00FFFFFF) | 0x22000000);
        bg.setStroke(dp(d, 1), color);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = wrap();
        lp.leftMargin = dp(d, 4);
        lp.rightMargin = dp(d, 4);
        t.setLayoutParams(lp);
        return t;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(float density, int v) {
        return Math.round(v * density);
    }
}
