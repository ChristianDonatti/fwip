#!/bin/bash
# Takes store and README screenshots on an emulator with sample comics. Run inside the emulator job.
set -x
APP=io.github.christiandonatti.fwip
OUT=shots
mkdir -p $OUT

# Screenshots are taken on the device and pulled, so nothing races with input running there.
shot() {
  sleep "${2:-2}"
  adb shell screencap -p /sdcard/shot.png
  adb pull /sdcard/shot.png "$OUT/$1.png" > /dev/null
  echo "::notice::$1: $(stat -c %s "$OUT/$1.png" 2>/dev/null) bytes"
}
ui() { python3 scripts/ui.py "$1"; }
center_tap() { adb shell input tap $(( $1 / 2 )) $(( $2 / 2 )); }

adb install -r app/build/outputs/apk/debug/app-debug.apk
# Straight into the app's own downloads folder, which the library always scans.
adb shell "run-as $APP mkdir -p files/Baixadas"
for f in comics/*.cbz; do
  b=$(basename "$f")
  adb exec-in "run-as $APP sh -c 'cat > files/Baixadas/$b'" < "$f"
done
echo "::notice::in app storage: $(adb shell "run-as $APP ls files/Baixadas" | wc -l) comics"
adb shell settings put system screen_off_timeout 1800000
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell svc power stayon true

run_device() {
  name=$1; w=$2; h=$3; dpi=$4
  adb shell wm size ${w}x${h}
  adb shell wm density $dpi
  adb shell am force-stop $APP
  adb shell am start -n $APP/.MainActivity
  sleep 25
  shot $name-1-home 3
  ui '^(Start|Continue|Read again)\|' || center_tap $w $h
  shot $name-2-reader 8
  # Turn a page half way: the curl follows the finger.
  adb shell "input swipe $(( w * 95 / 100 )) $(( h * 88 / 100 )) $(( w * 40 / 100 )) $(( h * 50 / 100 )) 6000 & sleep 3; screencap -p /sdcard/curl.png; wait"
  adb pull /sdcard/curl.png "$OUT/$name-3-curl.png" > /dev/null
  echo "::notice::$name-3-curl: $(stat -c %s "$OUT/$name-3-curl.png" 2>/dev/null) bytes"
  sleep 3
  center_tap $w $h
  sleep 1.5
  ui '^Balloons\||\|Balloons$' && shot $name-4-balloons 8
  ui '\|Leave guided reading' || adb shell input keyevent KEYCODE_BACK
  sleep 2
  center_tap $w $h
  sleep 1.5
  ui '\|Pages$' && shot $name-5-pages 5
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  adb shell input keyevent KEYCODE_BACK
  sleep 2
}

run_device tablet 1920 1200 240
run_device phone 1080 2400 420
adb shell wm size reset
adb shell wm density reset
echo "::notice::crashes: $(adb logcat -d -b crash | head -30 | tr '\n' ' ' | cut -c1-900)"
ls -la $OUT
