#!/usr/bin/env bash
# Emulator management for the Hello World test harness.
# Headless, software-rendered (this VM has no /dev/kvm), API 34 x86_64.
set -u
export JAVA_HOME="${JAVA_HOME:-$HOME/jdk}"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export ANDROID_AVD_HOME="$HOME/.android/avd"
EMU="$ANDROID_SDK_ROOT/emulator/emulator"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
AVD_NAME="${AVD_NAME:-muse-test}"
SERIAL="emulator-5554"

case "${1:-status}" in
  start)
    if "$ADB" devices | grep -q "$SERIAL[[:space:]]*device"; then
      echo "already running"; exit 0
    fi
    mkdir -p "$HOME/workspace/hello-world-android/tests/results"
    ANDROID_I_WANT_MY_TCG=yes nohup "$EMU" -avd "$AVD_NAME" -port 5554 -no-window -no-audio -no-boot-anim \
      -gpu swiftshader_indirect -accel off -memory 2048 -cores 2 \
      -no-snapshot \
      > "$HOME/workspace/hello-world-android/tests/results/emulator.log" 2>&1 &
    echo "emulator starting (pid $!)"
    ;;
  wait)
    "$ADB" -s "$SERIAL" wait-for-device
    for i in $(seq 1 120); do
      b=$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
      if [ "$b" = "1" ]; then echo "boot_completed after ~$((i*10))s"; break; fi
      sleep 10
    done
    # boot_completed alone is not enough: PackageManager lags behind on this
    # slow emulator, and installs fail with "Can't find service: package".
    for i in $(seq 1 60); do
      s=$("$ADB" -s "$SERIAL" shell service check package 2>/dev/null | tr -d '\r')
      if [ "$s" = "Service package: found" ]; then echo "package service ready"; exit 0; fi
      sleep 10
    done
    echo "PACKAGE SERVICE TIMEOUT"; exit 1
    ;;
  stop)
    "$ADB" -s "$SERIAL" emu kill || true
    ;;
  status)
    "$ADB" devices
    "$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null || true
    ;;
  *) echo "usage: emu.sh start|wait|stop|status"; exit 2 ;;
esac
