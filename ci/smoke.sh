#!/bin/bash
APK=$(ls app/build/outputs/apk/debug/*.apk | head -n 1)
adb install -r "$APK" || echo "::error::install failed"
adb logcat -c
adb shell am start -W -n com.hoursgram.app/.MainActivity
sleep 25
adb logcat -d > logcat.txt
if adb shell pidof com.hoursgram.app > /dev/null; then echo "::notice::app is alive after 25 s"; else echo "::error::app is DEAD after 25 s"; fi
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
SCREEN=$(adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | tr '\n' ' ')
echo "::notice::screen: ${SCREEN:0:600}"
CRASH=$(adb shell run-as com.hoursgram.app cat files/crash.txt 2>&1 | head -n 15)
echo "::notice::crash.txt: ${CRASH:0:900}"
grep -E "FATAL EXCEPTION|AndroidRuntime|Fatal signal|UnsatisfiedLink|at com.hoursgram|at dev.g000|libtd|backtrace|Process: com.hoursgram" logcat.txt | head -n 30 | while IFS= read -r line; do echo "::error::${line:0:700}"; done
