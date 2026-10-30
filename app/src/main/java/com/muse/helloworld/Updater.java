package com.muse.helloworld;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Updater {
    // Default update feed. Replace with the real repo URL once GitHub is connected;
    // also editable in the app (About page) without a rebuild.
    public static final String DEFAULT_FEED_URL =
        "https://raw.githubusercontent.com/hxbotai-create/hello-world-android/main/release/update.json";
    private static final String PREFS = "updater";
    private static final String KEY_FEED = "feed_url";

    public interface Callback {
        void onResult(boolean updateAvailable, int remoteCode, String remoteName, String apkUrl, String message);
    }

    public static String getFeedUrl(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getString(KEY_FEED, DEFAULT_FEED_URL);
    }
    public static void setFeedUrl(Context c, String url) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_FEED, url).apply();
    }
    public static long currentVersionCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) { return 0; }
    }
    public static String currentVersionName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    public static void check(final Activity act, final Callback cb) {
        final String feed = getFeedUrl(act);
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(feed).openConnection();
                conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/json");
                if (conn.getResponseCode() != 200) throw new Exception("Feed HTTP " + conn.getResponseCode());
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                JSONObject j = new JSONObject(sb.toString());
                final int remoteCode = j.getInt("versionCode");
                final String remoteName = j.optString("versionName", "" + remoteCode);
                final String apkUrl = j.getString("apkUrl");
                final boolean avail = remoteCode > currentVersionCode(act);
                act.runOnUiThread(() -> cb.onResult(avail, remoteCode, remoteName, apkUrl,
                    avail ? "Update available: v" + remoteName : "Up to date (v" + currentVersionName(act) + ")"));
            } catch (final Exception e) {
                act.runOnUiThread(() -> cb.onResult(false, -1, "", null, "Could not reach update feed: " + e.getMessage()));
            }
        }).start();
    }

    public static void downloadAndInstall(final Activity act, final String apkUrl) {
        if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(act, "Allow installs for this app, then tap update again", Toast.LENGTH_LONG).show();
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + act.getPackageName()));
            act.startActivity(s);
            return;
        }
        Toast.makeText(act, "Downloading update...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
                conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
                if (conn.getResponseCode() != 200) throw new Exception("APK HTTP " + conn.getResponseCode());
                File out = new File(act.getFilesDir(), "update.apk");
                InputStream in = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                fos.close(); in.close();
                act.runOnUiThread(() -> {
                    Uri uri = Uri.parse("content://com.muse.helloworld.updateprovider/update.apk");
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, "application/vnd.android.package-archive");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(i);
                });
            } catch (final Exception e) {
                act.runOnUiThread(() -> Toast.makeText(act, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
