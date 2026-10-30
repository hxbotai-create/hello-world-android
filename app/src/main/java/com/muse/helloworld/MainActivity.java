package com.muse.helloworld;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity implements SensorEventListener {
    private int count = 0;
    private View[] pages;
    private TextView[] navs;
    private GlassBackgroundView bg;
    private String[] paletteNames = {"Aurora", "Ocean", "Sunset", "Emerald"};
    private int currentPalette = 0;

    private SensorManager sm;
    private float smoothX = 0, smoothY = 0;
    private long lastShake = 0;
    private TextView compassText, cardinalText, lightText, batteryText, clockText, dateText, greetingText;
    private Handler handler = new Handler();
    private boolean torchOn = false;
    private String torchCameraId = null;

    // unfold animation
    private View unfoldOverlay, panelLeft, panelRight;
    private TextView duoTitle, duoSub, hingeText;
    private int lastBand = -1;
    private long lastUnfoldTrigger = 0;
    private boolean animating = false;

    private static final String[] CARDINALS = {"N","NE","E","SE","S","SW","W","NW"};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0B1026);
        getWindow().setNavigationBarColor(0xFF0B1026);
        setContentView(R.layout.activity_main);

        bg = findViewById(R.id.bgView);
        pages = new View[]{ findViewById(R.id.pageHome), findViewById(R.id.pageExplore), findViewById(R.id.pageLive), findViewById(R.id.pageAbout) };
        navs = new TextView[]{ (TextView)findViewById(R.id.navHome), (TextView)findViewById(R.id.navExplore), (TextView)findViewById(R.id.navLive), (TextView)findViewById(R.id.navAbout) };
        for (int i = 0; i < navs.length; i++) { final int idx = i; navs[i].setOnClickListener(v -> showPage(idx)); }

        clockText = findViewById(R.id.clockText);
        dateText = findViewById(R.id.dateText);
        greetingText = findViewById(R.id.greetingText);
        compassText = findViewById(R.id.compassText);
        cardinalText = findViewById(R.id.cardinalText);
        lightText = findViewById(R.id.lightText);
        batteryText = findViewById(R.id.batteryText);

        // Counter + haptics + milestones
        final TextView counterText = findViewById(R.id.counterText);
        Button tapButton = findViewById(R.id.tapButton);
        tapButton.setOnClickListener(v -> {
            count++;
            counterText.setText(count + (count == 1 ? " tap" : " taps"));
            buzz(15);
            v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
            if (count % 10 == 0) {
                buzz(80);
                applyPalette((currentPalette + 1) % paletteNames.length);
                Toast.makeText(this, count + " taps! Palette cycled to " + paletteNames[currentPalette], Toast.LENGTH_SHORT).show();
            }
        });

        // Palettes
        int[] paletteIds = {R.id.paletteAurora, R.id.paletteOcean, R.id.paletteSunset, R.id.paletteEmerald};
        for (int i = 0; i < paletteIds.length; i++) {
            final int idx = i;
            findViewById(paletteIds[i]).setOnClickListener(v -> applyPalette(idx));
        }

        // Glass Lab sliders
        SeekBar glowSeek = findViewById(R.id.glowSeek);
        glowSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { bg.setGlow(0.3f + p / 100f * 1.3f); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        SeekBar softSeek = findViewById(R.id.softSeek);
        softSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { bg.setSoftness(30f + p / 100f * 120f); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        // Torch
        Button torchBtn = (Button) findViewById(R.id.torchButton);
        torchBtn.setOnClickListener(v -> toggleTorch(torchBtn));

        // Unfold overlay
        unfoldOverlay = findViewById(R.id.unfoldOverlay);
        panelLeft = findViewById(R.id.panelLeft);
        panelRight = findViewById(R.id.panelRight);
        duoTitle = findViewById(R.id.duoTitle);
        duoSub = findViewById(R.id.duoSub);
        hingeText = findViewById(R.id.hingeText);
        unfoldOverlay.post(() -> {
            panelLeft.setPivotX(panelLeft.getWidth()); panelLeft.setPivotY(panelLeft.getHeight() / 2f);
            panelRight.setPivotX(0); panelRight.setPivotY(panelRight.getHeight() / 2f);
        });
        findViewById(R.id.previewUnfoldButton).setOnClickListener(v -> showUnfoldAnimation());
        lastBand = getSharedPreferences("fold", Context.MODE_PRIVATE).getInt("band", -1);

        // Sensors
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        setupUpdater();
        showPage(0);
    }

    private void applyPalette(int idx) {
        currentPalette = idx;
        bg.setPalette(idx);
    }

    private void buzz(long ms) {
        try {
            Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vb == null) return;
            if (Build.VERSION.SDK_INT >= 26) vb.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else vb.vibrate(ms);
        } catch (Exception ignored) {}
    }

    private void toggleTorch(Button btn) {
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (torchCameraId == null) {
                for (String id : cm.getCameraIdList()) {
                    Boolean flash = cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (flash != null && flash) { torchCameraId = id; break; }
                }
            }
            if (torchCameraId == null) { Toast.makeText(this, "No flashlight on this device", Toast.LENGTH_SHORT).show(); return; }
            torchOn = !torchOn;
            cm.setTorchMode(torchCameraId, torchOn);
            btn.setText(torchOn ? "Flashlight: On" : "Flashlight: Off");
        } catch (Exception e) {
            Toast.makeText(this, "Flashlight unavailable: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // --- sensors ---
    @Override protected void onResume() {
        super.onResume();
        if (sm != null) {
            Sensor acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (acc != null) sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI);
            Sensor rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rv != null) sm.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI);
            Sensor li = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (li != null) sm.registerListener(this, li, SensorManager.SENSOR_DELAY_UI);
            Sensor hinge = sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
            if (hinge != null) sm.registerListener(this, hinge, SensorManager.SENSOR_DELAY_UI);
            else if (hingeText != null) hingeText.setText("Hinge: no hinge sensor found - use Preview");
        }
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        handler.post(clockTick);
    }

    @Override protected void onPause() {
        super.onPause();
        if (sm != null) sm.unregisterListener(this);
        try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
        handler.removeCallbacks(clockTick);
    }

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            if (clockText != null) clockText.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
            if (dateText != null) dateText.setText(new SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now));
            if (greetingText != null) {
                int h = Integer.parseInt(new SimpleDateFormat("HH", Locale.getDefault()).format(now));
                greetingText.setText(h < 5 ? "Night owl" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening");
            }
            handler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int pct = scale > 0 ? Math.round(level * 100f / scale) : -1;
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            if (batteryText != null) batteryText.setText(pct + "% " + (charging ? "(charging)" : "") + (temp > 0 ? "\n" + (temp / 10f) + " C" : ""));
        }
    };

    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = e.values[0], y = e.values[1], z = e.values[2];
            smoothX = smoothX * 0.85f + (x / 9.81f) * 0.15f;
            smoothY = smoothY * 0.85f + (y / 9.81f) * 0.15f;
            bg.setTilt(smoothX, smoothY);
            double mag = Math.sqrt(x * x + y * y + z * z);
            long now = System.currentTimeMillis();
            if (mag > 27 && now - lastShake > 1500) {
                lastShake = now;
                int next = new Random().nextInt(paletteNames.length);
                applyPalette(next);
                buzz(40);
                Toast.makeText(this, "Shake! Palette: " + paletteNames[next], Toast.LENGTH_SHORT).show();
            }
        } else if (e.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rm = new float[9]; SensorManager.getRotationMatrixFromVector(rm, e.values);
            float[] ori = new float[3]; SensorManager.getOrientation(rm, ori);
            int deg = (int) ((Math.toDegrees(ori[0]) + 360) % 360);
            if (compassText != null) compassText.setText(deg + "\u00B0");
            if (cardinalText != null) cardinalText.setText("Compass - " + CARDINALS[Math.round(deg / 45f) % 8]);
        } else if (e.sensor.getType() == Sensor.TYPE_HINGE_ANGLE) {
            float ang = e.values[0];
            int band = ang < 45 ? 0 : ang < 150 ? 1 : 2;
            if (hingeText != null) hingeText.setText("Hinge: " + Math.round(ang) + "\u00B0 - " + (band == 0 ? "Folded" : band == 1 ? "Half open" : "Fully open"));
            if (band != lastBand) {
                boolean opening = lastBand != -1 && lastBand < 2 && band == 2;
                getSharedPreferences("fold", Context.MODE_PRIVATE).edit().putInt("band", band).apply();
                lastBand = band;
                if (opening && System.currentTimeMillis() - lastUnfoldTrigger > 4000) showUnfoldAnimation();
            }
        } else if (e.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = e.values[0];
            if (lightText != null) lightText.setText(Math.round(lux) + " lux\n" + (lux < 10 ? "Dark" : lux < 100 ? "Dim" : lux < 1000 ? "Indoor" : "Bright"));
        }
    }
    @Override public void onAccuracyChanged(Sensor s, int a) {}

    private void setupUpdater() {
        final TextView versionText = findViewById(R.id.versionText);
        versionText.setText("Version " + Updater.currentVersionName(this) + " (" + Updater.currentVersionCode(this) + ")");
        final TextView updateStatus = findViewById(R.id.updateStatus);
        final Button checkBtn = (Button) findViewById(R.id.checkUpdateButton);
        final EditText feedInput = (EditText) findViewById(R.id.feedUrlInput);
        feedInput.setText(Updater.getFeedUrl(this));
        final String[] pendingApk = {null};
        checkBtn.setOnClickListener(v -> {
            if (pendingApk[0] != null) { Updater.downloadAndInstall(this, pendingApk[0]); return; }
            updateStatus.setText("Checking...");
            Updater.check(this, (avail, code, name, apkUrl, msg) -> {
                updateStatus.setText(msg);
                if (avail) { pendingApk[0] = apkUrl; checkBtn.setText("Download & install v" + name); }
            });
        });
        findViewById(R.id.saveFeedButton).setOnClickListener(v -> {
            String u = feedInput.getText().toString().trim();
            if (u.length() > 0) {
                Updater.setFeedUrl(this, u);
                pendingApk[0] = null; checkBtn.setText("Check for updates");
                updateStatus.setText("Feed saved. Tap Check for updates.");
                Toast.makeText(this, "Update feed saved", Toast.LENGTH_SHORT).show();
            }
        });
        Updater.check(this, (avail, code, name, apkUrl, msg) -> {
            if (avail) {
                pendingApk[0] = apkUrl;
                updateStatus.setText(msg);
                checkBtn.setText("Download & install v" + name);
                Toast.makeText(this, "Update available: v" + name + " - see About page", Toast.LENGTH_LONG).show();
            }
        });
        Button hello = findViewById(R.id.helloButton);
        hello.setOnClickListener(v -> Toast.makeText(this, "Hello, Horatio! \uD83D\uDC4B from Muse", Toast.LENGTH_LONG).show());
    }

    private void showUnfoldAnimation() {
        if (animating || unfoldOverlay == null) return;
        animating = true;
        lastUnfoldTrigger = System.currentTimeMillis();
        buzz(25);
        unfoldOverlay.setVisibility(View.VISIBLE);
        unfoldOverlay.setAlpha(0f);
        unfoldOverlay.animate().alpha(1f).setDuration(180).start();
        panelLeft.setRotationY(-82f); panelRight.setRotationY(82f);
        panelLeft.animate().rotationY(0f).setDuration(750).setInterpolator(new DecelerateInterpolator()).start();
        panelRight.animate().rotationY(0f).setDuration(750).setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> buzz(15)).start();
        duoTitle.setAlpha(0f); duoTitle.setScaleX(0.75f); duoTitle.setScaleY(0.75f);
        duoTitle.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(220).setDuration(450)
            .setInterpolator(new DecelerateInterpolator()).start();
        duoSub.setAlpha(0f);
        duoSub.animate().alpha(1f).setStartDelay(380).setDuration(400).start();
        handler.postDelayed(() -> {
            unfoldOverlay.animate().alpha(0f).setDuration(350).withEndAction(() -> {
                unfoldOverlay.setVisibility(View.GONE);
                animating = false;
            }).start();
        }, 2500);
    }

    private void showPage(int idx) {
        for (int i = 0; i < pages.length; i++) {
            if (i == idx) {
                pages[i].setVisibility(View.VISIBLE);
                pages[i].setAlpha(0f); pages[i].setTranslationY(24f);
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
