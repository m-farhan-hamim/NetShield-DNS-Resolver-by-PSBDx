package com.example.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Checks the developer's own update-info endpoint for a newer version, and
 * can download + hand off the returned APK link to the system package
 * installer.
 *
 * IMPORTANT - F-Droid: F-Droid explicitly does not want apps that implement
 * their own update mechanism, since its own client already verifies and
 * delivers updates from a reproducible build it controls. So this whole
 * feature is a no-op whenever the app was installed via F-Droid - see
 * {@link #isSelfUpdateAllowed(Context)}, which every entry point below
 * checks first. Only direct (non-F-Droid) installs ever see it.
 */
public final class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String UPDATE_INFO_URL = "https://psbdx.com/wp-json/dns-app/v1/info";
    private static final String FDROID_INSTALLER_PACKAGE = "org.fdroid.fdroid";

    private UpdateChecker() {
    }

    public static class UpdateInfo {
        /** The version string reported by the server, e.g. "1.0.0". */
        public final String versionTag;
        /** Download link for the new version, as returned by the server. */
        public final String downloadUrl;

        public UpdateInfo(String versionTag, String downloadUrl) {
            this.versionTag = versionTag;
            this.downloadUrl = downloadUrl;
        }
    }

    public interface UpdateCallback {
        /** Called on a background thread. {@code info} is null if there's no update (or the check failed). */
        void onResult(UpdateInfo info);
    }

    public interface DownloadCallback {
        void onProgress(int percent);

        /** Called on a background thread with the downloaded file. */
        void onComplete(File apkFile);

        /** Called on a background thread. */
        void onError(Exception e);
    }

    public static boolean isSelfUpdateAllowed(Context context) {
        String installer = getInstallerPackageName(context);
        return !FDROID_INSTALLER_PACKAGE.equals(installer);
    }

    private static String getInstallerPackageName(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return pm.getInstallSourceInfo(context.getPackageName()).getInstallingPackageName();
            } else {
                return pm.getInstallerPackageName(context.getPackageName());
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Fire-and-forget background check. No-op (callback never fires) when {@link #isSelfUpdateAllowed} is false. */
    public static void checkForUpdate(final Context appContext, final UpdateCallback callback) {
        if (!isSelfUpdateAllowed(appContext)) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                UpdateInfo result = null;
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(UPDATE_INFO_URL).openConnection();
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(10_000);
                    conn.setReadTimeout(10_000);
                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_OK) {
                        JSONObject info = new JSONObject(readStream(conn.getInputStream()));
                        String version = info.optString("version", "");
                        String url = info.optString("url", "");
                        if (!version.isEmpty() && !url.isEmpty()
                                && isNewerVersion(version, currentVersionName(appContext))) {
                            result = new UpdateInfo(version, url);
                        }
                    } else {
                        Log.w(TAG, "Update check HTTP " + code);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Update check failed", e);
                } finally {
                    if (conn != null) conn.disconnect();
                }
                callback.onResult(result);
            }
        }, "UpdateCheckThread").start();
    }

    /**
     * Downloads the APK to app-private external storage and reports
     * progress/completion. Runs in the background. {@code downloadUrl} is
     * followed as-is (redirects included), so a server that returns a
     * freshly generated, expiring signed link for "url" works transparently
     * here - the request just follows it like any other HTTP redirect.
     */
    public static void downloadApk(final Context appContext, final String downloadUrl, final DownloadCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    File dir = new File(appContext.getExternalFilesDir(null), "updates");
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new IOException("Could not create updates directory");
                    }
                    File outFile = new File(dir, "netshield-update.apk");

                    conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(15_000);
                    conn.setReadTimeout(15_000);
                    int code = conn.getResponseCode();
                    if (code != HttpURLConnection.HTTP_OK) {
                        throw new IOException("HTTP " + code);
                    }
                    int totalSize = conn.getContentLength();
                    long downloaded = 0;
                    int lastPercent = -1;

                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            downloaded += read;
                            if (totalSize > 0) {
                                int percent = (int) (downloaded * 100 / totalSize);
                                if (percent != lastPercent) {
                                    lastPercent = percent;
                                    callback.onProgress(percent);
                                }
                            }
                        }
                    }
                    callback.onComplete(outFile);
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "UpdateDownloadThread").start();
    }

    private static String currentVersionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** Numeric, segment-by-segment comparison (handles "v1.4.0", "1.4", "1.4.0-beta", etc). */
    public static boolean isNewerVersion(String tag, String currentVersionName) {
        int[] tagParts = parseVersion(tag);
        int[] curParts = parseVersion(currentVersionName);
        int len = Math.max(tagParts.length, curParts.length);
        for (int i = 0; i < len; i++) {
            int t = i < tagParts.length ? tagParts[i] : 0;
            int c = i < curParts.length ? curParts[i] : 0;
            if (t != c) return t > c;
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        if (version == null || version.trim().isEmpty()) return new int[]{0};
        String cleaned = version.trim();
        if (cleaned.length() > 0 && (cleaned.charAt(0) == 'v' || cleaned.charAt(0) == 'V')) {
            cleaned = cleaned.substring(1);
        }
        int dash = cleaned.indexOf('-');
        if (dash >= 0) cleaned = cleaned.substring(0, dash);
        String[] segments = cleaned.split("\\.");
        int[] parts = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            String digitsOnly = segments[i].replaceAll("[^0-9]", "");
            parts[i] = digitsOnly.isEmpty() ? 0 : Integer.parseInt(digitsOnly);
        }
        return parts.length == 0 ? new int[]{0} : parts;
    }

    private static String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
