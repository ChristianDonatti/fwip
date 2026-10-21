#!/bin/bash
# Takes store and README screenshots on an emulator with sample comics. Run inside the emulator job.
set -x
APP=io.github.christiandonatti.fwip
OUT=shots
mkdir -p $OUT
shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; }
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
  sleep 6
  shot $name-2-reader 3
  # Turn a page half way: the curl follows the finger.
  adb shell input swipe $(( w * 95 / 100 )) $(( h * 90 / 100 )) $(( w * 45 / 100 )) $(( h * 55 / 100 )) 5000 &
  sleep 2.6
  adb exec-out screencap -p > "$OUT/$name-3-curl.png"
  wait
  sleep 3
  center_tap $w $h
  sleep 1.5
  ui '^Balloons\||\|Balloons$' && sleep 6 && shot $name-4-balloons 2
  ui '\|Leave guided reading' || adb shell input keyevent KEYCODE_BACK
  sleep 2
  center_tap $w $h
  sleep 1.5
  ui '\|Pages$' && shot $name-5-pages 4
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  adb shell input keyevent KEYCODE_BACK
  sleep 2
}

run_device tablet 1920 1200 240
run_device phone 1080 2400 420
adb shell wm size reset
adb shell wm density reset
ls -la $OUT
