#!/usr/bin/env python3
"""Emulator smoke tests for the Hello World app (com.muse.helloworld).

Runs against emulator-5554 (see emu.sh). Produces a results directory with
screenshots, UI dumps and RESULTS.md. Exit code 0 = all hard checks passed.

Usage: python3 emulator_test.py [--apk PATH] [--skip-update-check]
"""
import argparse, os, re, subprocess, sys, time, xml.etree.ElementTree as ET
from pathlib import Path

ADB = str(Path.home() / "android-sdk/platform-tools/adb")
SERIAL = "emulator-5554"
PKG = "com.muse.helloworld"
ACT = f"{PKG}/.MainActivity"

results = []  # (name, ok, hard, detail)

def adb(*args, timeout=120, check=False):
    r = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr}")
    return r

def shell(cmd, timeout=150):
    return adb("shell", cmd, timeout=timeout).stdout.strip()

def record(name, ok, detail="", hard=True):
    results.append((name, ok, hard, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}")

def dump_ui(outdir, tag):
    try:
        adb("shell", "uiautomator", "dump", "/sdcard/dump.xml", timeout=150)
        adb("pull", "/sdcard/dump.xml", str(outdir / f"{tag}.xml"), timeout=150)
        tree = ET.parse(outdir / f"{tag}.xml")
    except Exception as e:
        print(f"[warn] dump {tag} failed: {e}")
        return []
    nodes = []
    for n in tree.getroot().iter("node"):
        nodes.append(n.attrib)
    return nodes

def texts(nodes):
    return [n.get("text", "") for n in nodes if n.get("text")]

def find_center(nodes, label):
    for n in nodes:
        if n.get("text") == label:
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def tap(xy):
    adb("shell", "input", "tap", str(xy[0]), str(xy[1]), timeout=90)
    time.sleep(1.2)

def shot(outdir, tag):
    p = outdir / f"{tag}.png"
    with open(p, "wb") as f:
        subprocess.run([ADB, "-s", SERIAL, "exec-out", "screencap", "-p"], stdout=f, timeout=150)
    return p

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", default=str(Path(__file__).resolve().parent.parent / "build/hello-world.apk"))
    ap.add_argument("--skip-update-check", action="store_true")
    args = ap.parse_args()

    stamp = time.strftime("%Y%m%d-%H%M%S")
    outdir = Path(__file__).resolve().parent / "results" / stamp
    outdir.mkdir(parents=True, exist_ok=True)

    # 0. device booted? (retry: adb shell answers are flaky under TCG load)
    boot = ""
    for _ in range(6):
        boot = shell("getprop sys.boot_completed")
        if boot == "1":
            break
        pm = shell("service check package")
        if pm == "Service package: found":
            boot = "1 (pm-ready)"
            break
        time.sleep(10)
    record("emulator booted", boot.startswith("1"), f"sys.boot_completed={boot}")
    if not boot.startswith("1"):
        return finish(outdir)

    # 1. install
    r = adb("install", "-r", args.apk, timeout=300)
    combined = (r.stdout + " " + r.stderr).strip()
    record("apk installs", "Success" in combined, combined.splitlines()[-1][:160] if combined else "")
    if "Success" not in combined:
        return finish(outdir)

    # 2. launch, watch for crashes
    adb("logcat", "-c", timeout=90)
    adb("shell", "am", "force-stop", PKG, timeout=90)
    adb("shell", "am", "start", "-n", ACT, timeout=150)
    time.sleep(3)
    pid = shell(f"pidof {PKG}")
    log = adb("logcat", "-d", timeout=150).stdout
    fatal = "FATAL EXCEPTION" in log and PKG in log
    record("app launches and stays alive", bool(pid) and not fatal, f"pid={pid} fatal={fatal}")
    shot(outdir, "01-home")

    # 3. home page content
    nodes = dump_ui(outdir, "home")
    ts = texts(nodes)
    record("home shows Hello World", any("Hello World" in t for t in ts), "")
    record("home shows greeting + clock", any("Horatio" in t for t in ts), "")
    for label in ("Home", "Explore", "Live", "About"):
        record(f"nav has {label}", label in ts, "")

    # 4. counter increments
    c = find_center(nodes, "Tap me")
    if c:
        for _ in range(3): tap(c)
        ts2 = texts(dump_ui(outdir, "home-after-taps"))
        record("counter counts taps", any("Tapped 3 times" in t for t in ts2), str([t for t in ts2 if "Tapped" in t]))
    else:
        record("counter counts taps", False, "Tap me button not found")

    # 5. Explore page
    nodes = dump_ui(outdir, "home2")
    c = find_center(nodes, "Explore")
    if c:
        tap(c); shot(outdir, "02-explore")
        ts3 = texts(dump_ui(outdir, "explore"))
        record("explore page renders", any("Glass Lab" in t or "background" in t.lower() for t in ts3), "")
    else:
        record("explore page renders", False, "Explore nav not found")

    # 6. Live page + Duo fade preview
    nodes = dump_ui(outdir, "explore2")
    c = find_center(nodes, "Live")
    if c:
        tap(c); shot(outdir, "03-live")
        nodes = dump_ui(outdir, "live")
        ts4 = texts(nodes)
        record("live page shows hinge readout", any(t.startswith("Hinge:") for t in ts4), str([t for t in ts4 if t.startswith("Hinge:")])[:80])
        record("live page shows battery", any("Battery:" in t for t in ts4), "")
        c2 = find_center(nodes, "Preview the iPhone Duo fade")
        if c2:
            tap(c2)
            time.sleep(0.75)  # mid-dissolve
            shot(outdir, "04-fade-mid")
            mid = texts(dump_ui(outdir, "fade-mid"))
            record("fade preview shows cover card mid-dissolve", any("iPhone Duo" in t for t in mid), "")
            time.sleep(2.0)
            shot(outdir, "05-fade-done")
            pid2 = shell(f"pidof {PKG}")
            record("app alive after fade preview", bool(pid2), f"pid={pid2}")
        else:
            record("fade preview shows cover card mid-dissolve", False, "Preview button not found")
    else:
        record("live page shows hinge readout", False, "Live nav not found")

    # 7. About page + self-update check (soft: depends on emulator networking)
    nodes = dump_ui(outdir, "live2")
    c = find_center(nodes, "About")
    if c:
        tap(c); shot(outdir, "06-about")
        nodes = dump_ui(outdir, "about")
        ts5 = texts(nodes)
        m = [t for t in ts5 if re.match(r"v\d", t)]
        record("about page shows version", bool(m), str(m))
        if not args.skip_update_check:
            c3 = find_center(nodes, "Check for updates")
            if c3:
                tap(c3); time.sleep(6)
                ts6 = texts(dump_ui(outdir, "about-after-check"))
                status = [t for t in ts6 if "pdate" in t]
                ok = any(("available" in t or "Up to date" in t) for t in ts6)
                record("self-update check reaches a verdict", ok, str(status)[:120], hard=False)
    else:
        record("about page shows version", False, "About nav not found")

    # 8. crash sweep
    log = adb("logcat", "-d", timeout=150).stdout
    crashes = [ln for ln in log.splitlines() if "FATAL EXCEPTION" in ln]
    record("no fatal exceptions in logcat", not crashes, str(crashes[:1])[:120])
    return finish(outdir)

def finish(outdir):
    hard_fail = [r for r in results if not r[1] and r[2]]
    lines = [f"# Emulator test results\n"]
    for name, ok, hard, detail in results:
        lines.append(f"- [{'PASS' if ok else 'FAIL'}]{'' if hard else ' (soft)'} {name} — {detail}")
    lines.append(f"\nOverall: {'PASS' if not hard_fail else 'FAIL'} ({len(results)} checks)")
    (outdir / "RESULTS.md").write_text("\n".join(lines))
    print("\n".join(lines))
    print(f"Results in {outdir}")
    return 0 if not hard_fail else 1

if __name__ == "__main__":
    sys.exit(main())
