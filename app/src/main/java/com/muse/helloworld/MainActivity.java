package com.muse.helloworld;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;

public class MainActivity extends Activity {
    private int count = 0;
    private View[] pages;
    private TextView[] navs;
    private GlassBackgroundView bg;
    private String[] paletteNames = {"Aurora", "Ocean", "Sunset", "Emerald"};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // edge-to-edge-ish: dark status/nav bars to blend with aurora
        getWindow().setStatusBarColor(0xFF0B1026);
        getWindow().setNavigationBarColor(0xFF0B1026);
        setContentView(R.layout.activity_main);

        bg = findViewById(R.id.bgView);
        pages = new View[]{ findViewById(R.id.pageHome), findViewById(R.id.pageExplore), findViewById(R.id.pageAbout) };
        navs = new TextView[]{ (TextView)findViewById(R.id.navHome), (TextView)findViewById(R.id.navExplore), (TextView)findViewById(R.id.navAbout) };

        for (int i = 0; i < navs.length; i++) {
            final int idx = i;
            navs[i].setOnClickListener(v -> showPage(idx));
        }

        final TextView counterText = findViewById(R.id.counterText);
        Button tapButton = findViewById(R.id.tapButton);
        tapButton.setOnClickListener(v -> {
            count++;
            counterText.setText(count + (count == 1 ? " tap" : " taps"));
            v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
        });

        int[] paletteIds = {R.id.paletteAurora, R.id.paletteOcean, R.id.paletteSunset, R.id.paletteEmerald};
        for (int i = 0; i < paletteIds.length; i++) {
            final int idx = i;
            findViewById(paletteIds[i]).setOnClickListener(v -> {
                bg.setPalette(idx);
                Toast.makeText(this, paletteNames[idx] + " palette applied", Toast.LENGTH_SHORT).show();
            });
        }

        // --- self-updater (About page) ---
        final TextView versionText = findViewById(R.id.versionText);
        versionText.setText("Version " + Updater.currentVersionName(this) + " (" + Updater.currentVersionCode(this) + ")");
        final TextView updateStatus = findViewById(R.id.updateStatus);
        final Button checkBtn = (Button) findViewById(R.id.checkUpdateButton);
        final EditText feedInput = (EditText) findViewById(R.id.feedUrlInput);
        feedInput.setText(Updater.getFeedUrl(this));
        final String[] pendingApk = {null};
        final String[] pendingName = {null};
        View.OnClickListener doCheck = v -> {
            if (pendingApk[0] != null) { Updater.downloadAndInstall(this, pendingApk[0]); return; }
            updateStatus.setText("Checking...");
            Updater.check(this, (avail, code, name, apkUrl, msg) -> {
                updateStatus.setText(msg);
                if (avail) {
                    pendingApk[0] = apkUrl; pendingName[0] = name;
                    checkBtn.setText("Download & install v" + name);
                }
            });
        };
        checkBtn.setOnClickListener(doCheck);
        findViewById(R.id.saveFeedButton).setOnClickListener(v -> {
            String u = feedInput.getText().toString().trim();
            if (u.length() > 0) {
                Updater.setFeedUrl(this, u);
                pendingApk[0] = null; checkBtn.setText("Check for updates");
                updateStatus.setText("Feed saved. Tap Check for updates.");
                Toast.makeText(this, "Update feed saved", Toast.LENGTH_SHORT).show();
            }
        });
        // silent auto-check on launch: only nags if an update exists
        Updater.check(this, (avail, code, name, apkUrl, msg) -> {
            if (avail) {
                pendingApk[0] = apkUrl; pendingName[0] = name;
                updateStatus.setText(msg);
                checkBtn.setText("Download & install v" + name);
                Toast.makeText(this, "Update available: v" + name + " - see About page", Toast.LENGTH_LONG).show();
            }
        });

        Button hello = findViewById(R.id.helloButton);
        hello.setOnClickListener(v -> Toast.makeText(this, "Hello, Horatio! \uD83D\uDC4B from Muse", Toast.LENGTH_LONG).show());

        showPage(0);
    }

    private void showPage(int idx) {
        for (int i = 0; i < pages.length; i++) {
            if (i == idx) {
                pages[i].setVisibility(View.VISIBLE);
                pages[i].setAlpha(0f);
                pages[i].setTranslationY(24f);
                pages[i].animate().alpha(1f).translationY(0f).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator()).start();
                navs[i].setBackgroundResource(R.drawable.nav_selected);
                navs[i].setTextColor(0xFFFFFFFF);
            } else {
                pages[i].setVisibility(View.GONE);
                navs[i].setBackgroundResource(R.drawable.nav_idle);
                navs[i].setTextColor(0xCCFFFFFF);
            }
        }
    }
}
