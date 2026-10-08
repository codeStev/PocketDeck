#!/usr/bin/env python3
"""Starts a (release-type, R8-minified) build on an emulator, clicks through the welcome tour and every tab, and fails when
the app crashes or stops answering. Minification can break reflection-based code (Room, Hilt, serialization) in ways only a
running app shows, and the unit tests never run the minified code.

    scripts/smoke-test.py [--serial emulator-5554] path/to/app-release.apk

Emulators only: it taps blindly through the screen, which must never happen on a phone with the game on it."""
import argparse, re, subprocess, sys, time, xml.etree.ElementTree as ET

PACKAGE = "dev.pocketdeck"
TABS = ["Collection", "Decks", "Scan", "Settings"]
TOUR_BUTTONS = ["I understand", "Next"]  # the last page asks for "I understand", the others have "Next"


def adb(serial, *args, check=True):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, check=check).stdout


def screen_nodes(serial):
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return ET.fromstring(adb(serial, "exec-out", "cat", "/sdcard/ui.xml")).iter("node")


def tap(serial, label):
    """Taps the first node whose text or description is exactly [label]; False if the screen has none."""
    for node in screen_nodes(serial):
        if label in (node.get("text"), node.get("content-desc")):
            left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
            adb(serial, "shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            return True
    return False


def get_through_the_tour(serial):
    for _ in range(8):
        if any(tap(serial, button) for button in TOUR_BUTTONS):
            time.sleep(1)
        else:
            return


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        sys.exit(f"{args.serial} is not an emulator; this script taps blindly and must not run on a phone.")

    adb(args.serial, "install", "-r", args.apk)
    adb(args.serial, "logcat", "-c")
    adb(args.serial, "shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(8)
    get_through_the_tour(args.serial)
    missing = []
    for tab in TABS:
        if tap(args.serial, tab):
            time.sleep(3)
        else:
            missing.append(tab)

    log = adb(args.serial, "logcat", "-d", "-b", "crash,main", check=False)
    alive = adb(args.serial, "shell", "pidof", PACKAGE, check=False).strip()
    problems = []
    if "FATAL EXCEPTION" in log or f"ANR in {PACKAGE}" in log:
        problems.append("the app crashed or did not respond:\n" + "\n".join(l for l in log.splitlines() if "AndroidRuntime" in l)[:3000])
    if not alive:
        problems.append("the app is not running at the end")
    if missing:
        problems.append("tabs not found on screen: " + ", ".join(missing))
    if problems:
        sys.exit("SMOKE TEST FAILED\n" + "\n".join(problems))
    print(f"Smoke test passed: started, tour done, {len(TABS)} tabs opened, no crash.")


if __name__ == "__main__":
    main()
