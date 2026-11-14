#!/usr/bin/env python3
"""End-to-end test on an Android emulator: fake bank SMS -> expense list.

Installs the debug APK, enables SMS tracking, sets a PIN, opens the Expense
Tracker, sends SMS through the emulator console, then checks:
  * the app saved the real transactions and skipped OTP / personal / duplicate SMS
  * the open expense list updated by itself (no reopening)
  * it shows category groups and clean names, never raw SMS text
Screenshots go to ./emulator-shots for the workflow artifact.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = "com.androplus.pwdmgr.newapp"
APK = "app/build/outputs/apk/debug/app-debug.apk"
SHOTS = Path("emulator-shots")
PIN = "24680"
failures = []


def adb(*args, check=True, capture=True):
    r = subprocess.run(["adb", *args], capture_output=capture, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr.strip()}")
    return r.stdout


def shot(name):
    SHOTS.mkdir(exist_ok=True)
    png = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
    (SHOTS / f"{name}.png").write_bytes(png)


def ui_nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    return ET.fromstring(xml[xml.index("<"):]).iter("node")


def find(id_suffix=None, text=None):
    for n in ui_nodes():
        if id_suffix and n.get("resource-id", "").endswith(":id/" + id_suffix):
            return n
        if text and n.get("text") == text:
            return n
    return None


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def wait_for(id_suffix=None, text=None, timeout=30):
    end = time.time() + timeout
    while time.time() < end:
        node = find(id_suffix, text)
        if node is not None:
            return node
        time.sleep(1)
    shot("timeout")
    raise RuntimeError(f"timed out waiting for {id_suffix or text}")


def check(cond, msg):
    print(("PASS " if cond else "FAIL ") + msg)
    if not cond:
        failures.append(msg)


def send_sms(sender, text):
    adb("emu", "sms", "send", sender, *text.split(" "))
    time.sleep(2)


def main():
    adb("install", "-r", "-g", APK)   # -g grants SMS permissions up front

    # Turn on SMS tracking before the app first reads its settings.
    prefs = ('<?xml version="1.0" encoding="utf-8" standalone="yes" ?>'
             '<map><boolean name="sms_tracking_enabled" value="true" /></map>')
    subprocess.run(["adb", "shell", f"run-as {PKG} sh -c 'mkdir -p shared_prefs && "
                    f"cat > shared_prefs/{PKG}_preferences.xml'"],
                   input=prefs, text=True, check=True)

    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    tap(wait_for("firstPinView"))
    adb("shell", "input", "text", PIN)
    tap(wait_for("confirmPin"))
    adb("shell", "input", "text", PIN)
    tap(wait_for("expenseTrackerTile"))
    wait_for("expenseRecyclerView")
    time.sleep(2)
    shot("1-expense-list-empty")

    adb("logcat", "-c")
    send_sms("56767", "Rs 150.00 debited from a/c XX1234 to VPA swiggy@ybl UPI Ref 101. Avl bal: Rs 9,000 -Axis Bank")
    send_sms("56767", "Rs.1899 spent on your Axis Bank Credit Card XX5678 at AMAZON")
    send_sms("56767", "Rs.85,000.00 credited to A/c XX1234 by NEFT ACME SALARY")
    send_sms("56767", "123456 is your OTP for txn of Rs 2000 at Amazon. Do not share.")
    send_sms("9876543210", "Rs 500 paid to you for dinner, thanks")          # a person, not a bank
    send_sms("56767", "Rs 150.00 debited from a/c XX1234 to VPA swiggy@ybl UPI Ref 101. Avl bal: Rs 9,000 -Axis Bank")
    time.sleep(6)

    log = adb("logcat", "-d", "-s", "AutoTxnRecorder:D")
    saved = log.count("Saved SMS transaction: true")
    dupes = log.count("Skipped duplicate SMS transaction")
    print(log)
    check(saved == 3, f"3 real transactions saved (got {saved})")
    check(dupes == 1, f"repeated SMS skipped as duplicate (got {dupes})")

    # The list was open the whole time: it must have updated without reopening.
    shot("2-expense-list-live-update")
    headers = [n.get("text") for n in ui_nodes() if n.get("resource-id", "").endswith(":id/groupNameText")]
    print("category groups:", headers)
    check({"Food & Dining", "Shopping", "Income"} <= set(headers),
          f"grouped by category, list refreshed live (got {headers})")

    tap(wait_for(text="Food & Dining"))
    time.sleep(1)
    tap(wait_for(text="Shopping"))
    time.sleep(1)
    shot("3-categories-expanded")
    texts = [n.get("text") or "" for n in ui_nodes()]
    check("Swiggy" in texts and "AMAZON" in texts, "expanded rows show clean names (Swiggy, AMAZON)")
    check(not any("debited" in t or "XX1234" in t for t in texts), "raw SMS text is not shown")
    check(not any("OTP" in t for t in texts), "OTP message not in the list")

    if failures:
        print(f"\n{len(failures)} check(s) failed")
        sys.exit(1)
    print("\nAll emulator checks passed")


def diagnose():
    """Print what the emulator is showing, to make a failure readable from the log."""
    try:
        shot("error")
        print("--- resumed activity:")
        print("\n".join(l.strip() for l in adb("shell", "dumpsys", "activity", "activities",
                                               check=False).splitlines() if "mResumedActivity" in l))
        print("--- on screen (resource-id | text):")
        for n in ui_nodes():
            rid, txt = n.get("resource-id", ""), n.get("text", "")
            if rid or txt:
                print(f"  {rid.split('/')[-1]} | {txt}")
        print("--- crash log:")
        print(adb("logcat", "-d", "-b", "crash", check=False)[-4000:])
        print("--- app log (errors):")
        print(adb("logcat", "-d", "*:E", check=False)[-4000:])
    except Exception as e:
        print("diagnose failed:", e)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("ERROR:", e)
        diagnose()
        sys.exit(1)
