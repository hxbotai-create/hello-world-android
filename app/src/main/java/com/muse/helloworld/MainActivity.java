package com.muse.helloworld;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
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
import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.Shader;
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

    // Duo fade (hinge-driven dissolve)
    private View duoFadeLayer, coverCard, mainContainer;
    private TextView hingeText, sensorsText;
    private boolean fadePreviewRunning = false;
    private float dissolveProgress = 1f;
    private ValueAnimator dissolveAnimator;
    private Runnable settleRunnable;
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

        // Duo fade layer
        duoFadeLayer = findViewById(R.id.duoFadeLayer);
        coverCard = findViewById(R.id.coverCard);
        mainContainer = findViewById(R.id.mainContainer);
        hingeText = findViewById(R.id.hingeText);
        sensorsText = findViewById(R.id.sensorsText);
        findViewById(R.id.previewUnfoldButton).setOnClickListener(v -> playDuoFadePreview());
        lastBand = getSharedPreferences("fold", Context.MODE_PRIVATE).getInt("band", -1);

        // Sensors
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        setupUpdater();
        showPage(0);
    }

    // Fallback + primary unfold detection: display width class changes on fold/unfold.
    // Folded cover screen is narrow (~<500dp wide); the unfolded inner display is >=550dp.
    private void checkWidthUnfold() {
        int wDp = getResources().getConfiguration().screenWidthDp;
        if (wDp == Configuration.SCREEN_WIDTH_DP_UNDEFINED || wDp == 0) return;
        int nowBand = wDp >= 550 ? 1 : 0;
        android.content.SharedPreferences prefs = getSharedPreferences("fold", Context.MODE_PRIVATE);
        int prevBand = prefs.getInt("widthBand", -1);
        if (prevBand != nowBand) prefs.edit().putInt("widthBand", nowBand).apply();
        if (prevBand == 0 && nowBand == 1 && System.currentTimeMillis() - lastUnfoldTrigger > 4000) {
            handler.postDelayed(() -> playDuoFadePreview(), 450);
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        checkWidthUnfold();
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
            if (sensorsText != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("Hinge sensor: ").append(hinge != null ? hinge.getName() : "none").append("\nFold-related: ");
                int n = 0;
                for (Sensor sn : sm.getSensorList(Sensor.TYPE_ALL)) {
                    String nm = sn.getName().toLowerCase();
                    if (nm.contains("hinge") || nm.contains("fold") || nm.contains("posture")) { if (n++ > 0) sb.append(", "); sb.append(sn.getName()); }
                }
                if (n == 0) sb.append("none");
                sensorsText.setText(sb.toString());
            }
        }
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        handler.post(clockTick);
        checkWidthUnfold();
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
            // Fold 8 hinge reports are stepwise (only ~90 and ~180 observed),
            // so each report becomes a TARGET and the dissolve animates smoothly
            // to it - the Duo fade without needing a continuous angle stream.
            // On the cover display (narrow width) the app just runs normally.
            if (!fadePreviewRunning) {
                if (getResources().getConfiguration().screenWidthDp < 550) {
                    cancelDissolveAnim();
                    applyDissolve(1f);
                } else {
                    float target = (ang - 25f) / 130f;
                    if (target < 0f) target = 0f; if (target > 1f) target = 1f;
                    animateDissolveTo(target);
                }
            }
            if (band != lastBand) {
                boolean opening = lastBand != -1 && lastBand < 2 && band == 2;
                getSharedPreferences("fold", Context.MODE_PRIVATE).edit().putInt("band", band).apply();
                lastBand = band;
                if (opening && System.currentTimeMillis() - lastUnfoldTrigger > 4000) {
                    lastUnfoldTrigger = System.currentTimeMillis();
                    buzz(20);
                }
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

    // progress 0 = cover screen shown, main UI dissolved away.
    // progress 1 = inner screen fully in focus, cover gone.
    private void cancelDissolveAnim() {
        if (dissolveAnimator != null) { dissolveAnimator.cancel(); dissolveAnimator = null; }
        if (settleRunnable != null) { handler.removeCallbacks(settleRunnable); settleRunnable = null; }
    }

    private void animateDissolveTo(final float target) {
        if (Math.abs(target - dissolveProgress) < 0.01f) return;
        cancelDissolveAnim();
        final float from = dissolveProgress;
        long dur = 450 + (long) (650 * Math.abs(target - from));
        ValueAnimator anim = ValueAnimator.ofFloat(from, target);
        anim.setDuration(dur);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> applyDissolve((Float) a.getAnimatedValue()));
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (dissolveAnimator == a) dissolveAnimator = null;
                // Parked mid-fold (laptop posture): hold the effect briefly,
                // then settle back to a fully readable UI.
                if (target > 0.03f && target < 0.97f) {
                    settleRunnable = () -> animateDissolveTo(1f);
                    handler.postDelayed(settleRunnable, 1200);
                }
            }
        });
        dissolveAnimator = anim;
        anim.start();
    }

    private void applyDissolve(float pr) {
        dissolveProgress = pr;
        if (coverCard == null || mainContainer == null) return;
        if (pr >= 0.985f) {
            duoFadeLayer.setVisibility(View.GONE);
            mainContainer.setAlpha(1f);
            mainContainer.setScaleX(1f); mainContainer.setScaleY(1f);
            clearBlur();
            return;
        }
        duoFadeLayer.setVisibility(View.VISIBLE);
        // cover dissolves: fades out while expanding toward the viewer (screen "grows")
        coverCard.setAlpha(1f - pr);
        float cs = 0.9f + 0.35f * pr;
        coverCard.setScaleX(cs); coverCard.setScaleY(cs);
        // inner content fades in and sharpens (scale settles, blur clears)
        mainContainer.setAlpha(0.2f + 0.8f * pr);
        float ms = 0.94f + 0.06f * pr;
        mainContainer.setScaleX(ms); mainContainer.setScaleY(ms);
        applyBlur((1f - pr) * 18f);
    }

    private void applyBlur(float radius) {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            if (radius < 0.5f) mainContainer.setRenderEffect(null);
            else mainContainer.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        } catch (Exception ignored) {}
    }
    private void clearBlur() {
        if (Build.VERSION.SDK_INT < 31) return;
        try { mainContainer.setRenderEffect(null); } catch (Exception ignored) {}
    }

    // Timed version for Preview and for the discrete width-change trigger.
    private void playDuoFadePreview() {
        if (fadePreviewRunning) return;
        cancelDissolveAnim();
        fadePreviewRunning = true;
        buzz(15);
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        // Emulators (esp. software-rendered) take seconds per screenshot, so a
        // 1.5s preview is unobservable there - stretch it when on an emulator.
        boolean emu = Build.FINGERPRINT.toLowerCase().contains("generic")
            || Build.FINGERPRINT.toLowerCase().contains("emulator")
            || Build.MODEL.toLowerCase().contains("emulator")
            || Build.MODEL.toLowerCase().contains("sdk gphone")
            || Build.MANUFACTURER.toLowerCase().contains("unknown");
        anim.setDuration(emu ? 12000 : 1500);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> applyDissolve((Float) a.getAnimatedValue()));
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                fadePreviewRunning = false;
                applyDissolve(1f);
                buzz(15);
            }
        });
        anim.start();
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
