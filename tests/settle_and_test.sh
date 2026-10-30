#!/usr/bin/env bash
# Wait for the slow TCG emulator to fully settle (PackageManager responsive
# 3x in a row), then run the test suite. For background use.
set -u
ADB="$HOME/android-sdk/platform-tools/adb"
S="emulator-5554"
ok=0
for i in $(seq 1 90); do
  r=$("$ADB" -s "$S" shell service check package 2>/dev/null | tr -d '\r')
  if [ "$r" = "Service package: found" ]; then ok=$((ok+1)); else ok=0; fi
  echo "$(date +%H:%M:%S) try$i package_streak=$ok"
  [ "$ok" -ge 3 ] && break
  sleep 30
done
[ "$ok" -ge 3 ] || { echo "SETTLE TIMEOUT"; exit 1; }
cd "$HOME/workspace/hello-world-android"
python3 tests/emulator_test.py --apk build/hello-world.apk
