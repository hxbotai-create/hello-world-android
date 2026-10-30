# Emulator testing

Goal: verify every build on a real (emulated) Android device **before** code is
pushed or a release is published.

## Environment (this VM)
- Android Emulator 37.3.6 + API 34 (Android 14) x86_64 "default" system image,
  installed under `~/android-sdk` (emulator/, system-images/android-34/default/x86_64).
- **No /dev/kvm** on this VM, so the emulator runs with forced TCG software
  emulation (`ANDROID_I_WANT_MY_TCG=yes`, `-accel off`) and SwiftShader
  software GPU. Boot takes several minutes; UI is slow but functional.
  2 vCPU, 2 GB guest RAM, 1080x2400.
- AVD name: `muse-test` (`~/.android/avd/`).

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
