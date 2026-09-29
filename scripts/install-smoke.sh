#!/usr/bin/env bash
set -euo pipefail

apk="app/build/outputs/apk/debug/app-debug.apk"
build_tools="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/35.0.0"

"$build_tools/apksigner" verify --verbose --print-certs "$apk"
"$build_tools/aapt" dump badging "$apk"
sleep 20 # Give the emulator launcher time to finish its first boot.
adb install -r "$apk"
adb logcat -c
adb shell am start -W -n ru.vkusdetstva/.MainActivity
sleep 5
adb shell pidof ru.vkusdetstva
if adb logcat -d -v brief | grep -E 'FATAL EXCEPTION|Process: ru.vkusdetstva'; then
    echo 'App crashed after installation.' >&2
    exit 1
fi
assert_screen() {
    local expected="$1"
    adb shell uiautomator dump /sdcard/vkus-window.xml >/dev/null
    adb exec-out cat /sdcard/vkus-window.xml > app/build/outputs/vkus-window.xml
    if grep -Eiq 'Quickstep.*(responding|отвечает)|System UI.*(responding|отвечает)' app/build/outputs/vkus-window.xml; then
        echo 'The emulator system is showing an ANR dialog; UI screenshots would be invalid.' >&2
        exit 1
    fi
    if ! grep -Fq "$expected" app/build/outputs/vkus-window.xml; then
        echo "Expected screen text missing: $expected" >&2
        exit 1
    fi
}
assert_screen 'Вкус детства'
adb shell screencap -p /sdcard/vkus-home.png
adb pull /sdcard/vkus-home.png app/build/outputs/vkus-home.png
adb shell input tap 963 2235
sleep 2
assert_screen 'Тёмная тема'
adb shell screencap -p /sdcard/vkus-settings.png
adb pull /sdcard/vkus-settings.png app/build/outputs/vkus-settings.png
adb shell input tap 970 552
sleep 2
assert_screen 'Тёмная тема'
adb shell screencap -p /sdcard/vkus-dark.png
adb pull /sdcard/vkus-dark.png app/build/outputs/vkus-dark.png
adb shell input tap 320 2235
sleep 2
assert_screen 'Поиск в семейном архиве'
adb shell screencap -p /sdcard/vkus-search.png
adb pull /sdcard/vkus-search.png app/build/outputs/vkus-search.png
if cmp -s app/build/outputs/vkus-settings.png app/build/outputs/vkus-dark.png; then
    echo 'The theme toggle did not change the settings screen.' >&2
    exit 1
fi
