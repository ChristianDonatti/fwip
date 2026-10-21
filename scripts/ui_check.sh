#!/bin/bash
# Walks through the library look on an emulator: welcome, light theme, accents, cover sizes,
# series backdrop, the cover growing into the reader. Screenshots go to repro/.
set -x
APP=io.github.christiandonatti.fwip
OUT=repro
mkdir -p $OUT
W=1920; H=1200

shot() {
  sleep "${2:-2}"
  timeout 40 adb shell screencap -p /sdcard/shot.png
  timeout 40 adb pull /sdcard/shot.png "$OUT/$1.png" > /dev/null
}
# No pause: for catching an animation half way.
quick() {
  timeout 20 adb shell screencap -p /sdcard/q.png
  timeout 20 adb pull /sdcard/q.png "$OUT/$1.png" > /dev/null
}
ui() { python3 scripts/ui.py "$1"; }
find_xy() { python3 -c "import sys; sys.path.insert(0,'scripts'); import ui; p=ui.find(sys.argv[1]); print(f'{p[0]} {p[1]}' if p else '')" "$1"; }
restart() { adb shell am force-stop $APP; adb shell am start -n $APP/.MainActivity; sleep "${1:-12}"; }
prefs() {
  # Writes the look settings straight into the app's preferences, then restarts it.
  adb shell am force-stop $APP
  adb exec-in "run-as $APP sh -c 'mkdir -p shared_prefs; cat > shared_prefs/fwip_ui.xml'" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="theme">$1</string>
    <string name="accent">$2</string>
    <float name="coverScale" value="$3" />
    <boolean name="welcomed" value="true" />
</map>
XML
  restart 14
}

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell svc power stayon true
adb shell wm size ${W}x${H}
adb shell wm density 240

# ---- First launch ----
adb shell pm clear $APP
restart 12
shot w1-welcome 2
ui '^Continue\|' ; shot w2-look 2
ui '^Light\|' ; shot w2-look-light 2
ui '\|Blue$' ; shot w2-look-blue 2
ui '^Continue\|' ; shot w3-comics 2
ui '^Later\|' ; shot w4-after 3

# ---- Comics in, light theme, blue ----
adb shell "run-as $APP mkdir -p files/Baixadas"
for f in comics/PepperCarrot_*.cbz; do
  b=$(basename "$f")
  adb exec-in "run-as $APP sh -c 'cat > files/Baixadas/$b'" < "$f"
done
restart 25
shot l1-home-light 3
ui '^Library\|' ; shot l2-library-light 3

# Options sheet tinted by the cover, then the series with its backdrop.
xy=$(find_xy '\|Pepper')
if [ -n "$xy" ]; then
  set -- $xy
  adb shell input swipe $1 $2 $1 $2 900
  shot l3-options 2
  ui '^See series' ; shot l4-series 3
  # Tap a cover and catch it growing.
  xy2=$(find_xy '\|Pepper')
  set -- $xy2
  adb shell input tap $1 $2
  sleep 0.12; quick z1-opening-a
  quick z1-opening-b
  shot z2-reader 6
  # Reader settings with the new switches.
  adb shell input tap $(( W / 2 )) $(( H / 2 )); sleep 1.5
  ui '\|Reading settings' ; sleep 1
  adb shell input swipe $(( W / 2 )) $(( H * 3 / 4 )) $(( W / 2 )) $(( H / 4 )) 400
  shot r1-settings 2
  adb shell input keyevent KEYCODE_BACK; sleep 1
  adb shell input keyevent KEYCODE_BACK; sleep 1
  adb shell input keyevent KEYCODE_BACK
  sleep 0.1; quick z3-closing-a
  quick z3-closing-b
  shot z4-back 3
fi

# Appearance dialog from the folder button.
adb shell input keyevent KEYCODE_BACK; sleep 1
ui '\|Library folders' ; sleep 1.5
ui '^Appearance\|' ; shot a1-appearance 2
adb shell input keyevent KEYCODE_BACK

# Other looks.
prefs Dark Green 1.0
shot d1-home-dark-green 3
prefs Light Pink 1.7
ui '^Library\|' ; shot d2-library-big-pink 3
prefs Dark Amber 0.65
ui '^Library\|' ; shot d3-library-small 3

# Phone, light theme.
adb shell wm size 1080x2400
adb shell wm density 420
prefs Light Violet 1.0
shot p1-phone-home 3
xy=$(find_xy '\|Pepper')
if [ -n "$xy" ]; then
  set -- $xy
  adb shell input tap $1 $2
  sleep 0.12; quick p2-opening
  shot p3-reader 6
fi
adb shell wm size reset
adb shell wm density reset
timeout 30 adb logcat -d -b crash > $OUT/crash.txt
ls -la $OUT
