# Emulator testing

Goal: verify every build on a real (emulated) Android device **before** code is
pushed or a release is published.

## Environment (this VM)
- Android Emulator 37.3.6, installed under `~/android-sdk` (emulator/,
  system-images/...). **Active test AVD: `muse-test25` — API 25 (Android 7.1)
  "default" x86_64 image, 720x1280, 1.5 GB guest RAM.**
- Why API 25: the API 34 and API 30 images never stabilize on this VM.
  With **no /dev/kvm** (forced TCG software emulation,
  `ANDROID_I_WANT_MY_TCG=yes`, `-accel off`, SwiftShader GPU, 2 vCPU),
  their system_server main thread blocks so long that the Android watchdog
  kills it in a loop — the `package` service flaps and every install fails
  with "Can't find service: package" / binder broken pipe, even though
  package scanning completes. API 25's much lighter system stays up:
  boot-to-package-ready in ~4-6 min, installs succeed.
- Trade-offs of API 25: RenderEffect blur (API 31+) skips via its guard;
  no hinge sensor (Fold-only path untestable here); `uiautomator dump`
  fails with "null root node returned by UiTestAutomationBridge", so
  text assertions in emulator_test.py don't work on this image — use
  coordinate taps + screenshots (screenshot latency is ~10s, so the app
  stretches the fade Preview to 12s on emulators to make it observable).
- AVDs `muse-test` (API 34) and `muse-test30` (API 30) still exist but are
  not usable here; don't waste time re-trying them on this VM.

## Run
```bash
tests/emu.sh start          # boots headless in background (log: tests/results/emulator.log)
tests/emu.sh wait           # blocks until sys.boot_completed=1
python3 tests/emulator_test.py --apk build/hello-world.apk
tests/emu.sh stop
```
Results land in `tests/results/<timestamp>/`: screenshots (home, explore,
live, mid-fade, after-fade, about), UI hierarchy dumps per step, and
`RESULTS.md` with PASS/FAIL per check. Exit code 0 = all hard checks pass.
The self-update verdict check is *soft* (depends on emulator networking).

## What is covered
Install-in-place, cold launch + crash watch, home content, tap counter,
page navigation, Live page sensor readouts, the Duo fade Preview
(mid-dissolve screenshot proving the cover card renders), About version,
logcat fatal-exception sweep.

## Known limits
- The generic AVD has no hinge-angle sensor, so the hinge-driven dissolve
  can't be exercised end-to-end here; the Preview path uses the same
  `applyDissolve()` code and is covered. Fold posture/width triggers need a
  foldable AVD profile — future work.
- Physical-device behaviors (real sensors, Samsung installer quirks) still
  need an on-device check; the emulator catches crashes and UI regressions.
