# Hello World — Liquid Glass

A tiny native Android app built by Muse for Horatio. No libraries, no Gradle —
just `aapt2` / `javac` / `d8` via `build.sh`.

**Package:** `com.muse.helloworld` · **Version:** 4.0 · **Min SDK:** 24 (Android 7.0) · **Target SDK:** 34

## Features
- Liquid-glass UI: frosted translucent cards with gradient sheen and specular
  borders over a soft-blurred aurora background (`GlassBackgroundView`)
- 3 pages with a floating glass bottom nav — Home, Explore, About
- Explore palettes (Aurora, Ocean, Sunset, Emerald) restyle the whole background
- Home tap counter with a press animation

## Build
Requires a JDK 17 and Android SDK platform 34 + build-tools 34.0.0.

```bash
export JAVA_HOME=/path/to/jdk17
# expects the SDK at ~/android-sdk (edit ANDROID_HOME in build.sh if different)
./build.sh
# output: build/hello-world.apk (debug-signed)
```

The signing keystore (`debug.keystore`) is generated on first build and is
gitignored — keep your copy safe: Android only accepts in-place updates signed
with the same key.

## Install
Sideload the APK on the phone (tap it, allow install from that source), or:

```bash
adb install -r build/hello-world.apk
```

## Layout
- `app/src/main/java/com/muse/helloworld/MainActivity.java` — pages, nav, interactions
- `app/src/main/java/com/muse/helloworld/GlassBackgroundView.java` — aurora blobs + blur
- `app/src/main/res/` — layouts and glass drawables

## Self-updating (v3.0+)

On launch the app checks an update feed; the About page also has a manual
**Check for updates** button and an editable feed URL (saved on-device, no
rebuild needed to change it).

Feed format (`update.json`, served over HTTPS):

```json
{
  "versionCode": 4,
  "versionName": "3.1",
  "apkUrl": "https://<host>/hello-world.apk",
  "notes": "What changed"
}
```

If `versionCode` is higher than the installed build, the app offers
**Download & install**: it downloads the APK to internal storage, serves it to
the system installer through `UpdateProvider` (a `content://` URI — `file://`
is blocked on Android 7+), and the user confirms once in the installer
(Android does not allow silent installs for regular apps). First use requires
allowing "install unknown apps" for this app. All builds must keep the same
signing key, or Android will refuse the update.

Default feed:
`https://raw.githubusercontent.com/hxbotai-create/hello-world-android/main/release/update.json`
— set in `Updater.DEFAULT_FEED_URL`, overridable in-app on the About page.

**To publish an update:** bump `versionCode`/`versionName` in the manifest,
`./build.sh`, host the APK at a stable HTTPS URL, and point `update.json` at it
with the new version code. Installed v3+ apps pick it up on next launch.
