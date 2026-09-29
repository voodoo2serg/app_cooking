#!/usr/bin/env bash
set -euo pipefail

apk="app/build/outputs/apk/debug/app-debug.apk"
build_tools="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/35.0.0"

"$build_tools/apksigner" verify --verbose --print-certs "$apk"
"$build_tools/aapt" dump badging "$apk"
adb install -r "$apk"
adb logcat -c
adb shell am start -W -n ru.vkusdetstva/.MainActivity
sleep 5
adb shell pidof ru.vkusdetstva
if adb logcat -d -v brief | grep -E 'FATAL EXCEPTION|Process: ru.vkusdetstva'; then
    echo 'App crashed after installation.' >&2
    exit 1
fi
adb shell screencap -p /sdcard/vkus-home.png
adb pull /sdcard/vkus-home.png app/build/outputs/vkus-home.png
adb shell input tap 963 2235
sleep 2
adb shell screencap -p /sdcard/vkus-settings.png
adb pull /sdcard/vkus-settings.png app/build/outputs/vkus-settings.png
