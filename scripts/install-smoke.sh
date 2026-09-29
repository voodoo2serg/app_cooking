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

# Находит узел с точным текстом (uiautomator dump) и тапает его центр;
# при необходимости прокручивает экран вниз небольшими шагами.
find_and_tap() {
    local text="$1" bounds=""
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        adb shell uiautomator dump /sdcard/vkus-window.xml >/dev/null
        adb exec-out cat /sdcard/vkus-window.xml > app/build/outputs/vkus-window.xml
        bounds=$(grep -o "text=\"$text\"[^>]*" app/build/outputs/vkus-window.xml | \
            grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1)
        if [ -n "$bounds" ]; then
            local nums x1 y1 x2 y2
            nums=$(echo "$bounds" | sed -e 's/\]\[/,/g' -e 's/[^0-9,]//g')
            x1=$(echo "$nums" | cut -d, -f1); y1=$(echo "$nums" | cut -d, -f2)
            x2=$(echo "$nums" | cut -d, -f3); y2=$(echo "$nums" | cut -d, -f4)
            adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
            return 0
        fi
        adb shell input swipe 540 1600 540 1000 300
        sleep 1
    done
    echo "Node not found: $text" >&2
    return 1
}

# Настройки больше не в нижнем меню — открываем карточкой на главной.
find_and_tap 'Настройки'
sleep 2
assert_screen 'Профиль автора'
adb shell screencap -p /sdcard/vkus-settings.png
adb pull /sdcard/vkus-settings.png app/build/outputs/vkus-settings.png

# The settings page keeps growing (import section), so a single long swipe can
# fling past the theme switch. Scroll in small controlled steps until the only
# checkable node on the screen - the switch - is fully visible, then tap it.
rect=""
for _ in 1 2 3 4 5 6 7 8; do
    adb shell uiautomator dump /sdcard/vkus-window.xml >/dev/null
    adb exec-out cat /sdcard/vkus-window.xml > app/build/outputs/vkus-window.xml
    rect=$(grep -o 'checkable="true"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
        app/build/outputs/vkus-window.xml | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]')
    if [ -n "$rect" ]; then
        # bounds="[x1,y1][x2,y2]" -> strip brackets, then split into 4 numbers
        x1=$(echo "$rect" | sed -e 's/\]\[/,/g' -e 's/[^0-9,]//g' | cut -d, -f1)
        y1=$(echo "$rect" | sed -e 's/\]\[/,/g' -e 's/[^0-9,]//g' | cut -d, -f2)
        x2=$(echo "$rect" | sed -e 's/\]\[/,/g' -e 's/[^0-9,]//g' | cut -d, -f3)
        y2=$(echo "$rect" | sed -e 's/\]\[/,/g' -e 's/[^0-9,]//g' | cut -d, -f4)
        if [ "$y1" -ge 140 ] && [ "$y2" -le 2140 ]; then break; fi
        rect=""
        if [ "$y2" -le 140 ]; then
            adb shell input swipe 540 1000 540 1600 300 # scrolled past it: back up
        else
            adb shell input swipe 540 1600 540 1000 300 # below the fold: scroll on
        fi
    else
        adb shell input swipe 540 1600 540 1000 300 # switch not found in this dump
    fi
    sleep 1
done
if [ -z "$rect" ]; then
    echo 'Theme switch not found on the settings screen.' >&2
    exit 1
fi

if grep -Eiq 'Quickstep.*(responding|отвечает)|System UI.*(responding|отвечает)' app/build/outputs/vkus-window.xml; then
    echo 'The emulator system is showing an ANR dialog; UI screenshots would be invalid.' >&2
    exit 1
fi
if ! grep -Fq 'Тёмная тема' app/build/outputs/vkus-window.xml; then
    echo 'Expected screen text missing: Тёмная тема' >&2
    exit 1
fi
x1=$(echo "$rect" | sed -e 's/\]\[/,/g' -e 's/[^0-9,]//g' | cut -d, -f1)
y1=$(echo "$rect" | sed -e 's/\]\[/,/g' -e 's/[^0-9,]//g' | cut -d, -f2)
x2=$(echo "$rect" | sed -e 's/\]\[/,/g' -e 's/[^0-9,]//g' | cut -d, -f3)
y2=$(echo "$rect" | sed -e 's/\]\[/,/g' -e 's/[^0-9,]//g' | cut -d, -f4)
adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
sleep 2
adb shell screencap -p /sdcard/vkus-dark.png
adb pull /sdcard/vkus-dark.png app/build/outputs/vkus-dark.png
if cmp -s app/build/outputs/vkus-settings.png app/build/outputs/vkus-dark.png; then
    echo 'The theme toggle did not change the settings screen.' >&2
    exit 1
fi

# Нижнее меню: Поиск → Лента → Корзина.
adb shell input tap 320 2235
sleep 2
assert_screen 'Поиск в семейном архиве'
adb shell screencap -p /sdcard/vkus-search.png
adb pull /sdcard/vkus-search.png app/build/outputs/vkus-search.png
adb shell input tap 756 2235
sleep 2
assert_screen 'Лента семейного стола'
adb shell screencap -p /sdcard/vkus-feed.png
adb pull /sdcard/vkus-feed.png app/build/outputs/vkus-feed.png
adb shell input tap 963 2235
sleep 2
assert_screen 'Корзина'
adb shell screencap -p /sdcard/vkus-basket.png
adb pull /sdcard/vkus-basket.png app/build/outputs/vkus-basket.png
