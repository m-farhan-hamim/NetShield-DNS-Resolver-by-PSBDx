package com.example.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.R;
import com.example.proxy.KnownProxyApps;

/**
 * Every outside link NetShield offers for help, in one place. All of them are plain links opened
 * in the user's own browser or Telegram app: no SDK, no in-app web view, nothing is sent from here.
 */
public final class HelpLinks {

    public static final String GITHUB_ISSUES = "https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/issues";
    /** The suggested way to get help. */
    public static final String TELEGRAM_BOT = "https://t.me/mfh_PSBDx_bot";
    public static final String TELEGRAM_COMMUNITY = "https://t.me/psbdx_bd_comm";
    public static final String WIKI = "https://wiki.psbdx.com/category/netshield-dns-resolver";
    public static final String DOCS_ORBOT = "https://wiki.psbdx.com/wiki/how-to-switch-orbot-to-proxy-mode-only";
    public static final String DOCS_INVIZIBLE = "https://wiki.psbdx.com/wiki/how-to-switch-invizible-pro-to-proxy-mode-only";
    public static final String DOCS_V2RAYNG = "https://wiki.psbdx.com/wiki/how-to-switch-v2rayng-to-proxy-mode-only";

    private HelpLinks() {
    }

    /** The "switch this app to proxy mode only" guide for a known proxy app, or null for a custom one. */
    @Nullable
    public static String docsFor(@Nullable KnownProxyApps.App app) {
        if (app == KnownProxyApps.V2RAYNG) {
            return DOCS_V2RAYNG;
        }
        if (app == KnownProxyApps.ORBOT) {
            return DOCS_ORBOT;
        }
        if (app == KnownProxyApps.INVIZIBLE) {
            return DOCS_INVIZIBLE;
        }
        return null;
    }

    public static void open(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.about_link_error, Toast.LENGTH_SHORT).show();
        }
    }
}
