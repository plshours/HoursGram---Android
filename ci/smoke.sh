#!/bin/sh
APK=$(ls app/build/outputs/apk/release/*.apk | head -n 1)
adb install -r "$APK" || echo "::error::install failed"
adb logcat -c
adb shell am start -W -n com.hoursgram.app/.MainActivity
sleep 25
adb logcat -d > logcat.txt
if adb shell pidof com.hoursgram.app > /dev/null; then echo "::notice::app is alive after 25 s"; else echo "::error::app is DEAD after 25 s"; fi
grep -E "FATAL|AndroidRuntime|Fatal signal|UnsatisfiedLink|Caused by|hoursgram|libtd" logcat.txt | head -n 40 | while IFS= read -r line; do echo "::error::${line:0:700}"; done
