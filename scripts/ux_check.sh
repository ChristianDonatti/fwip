#!/bin/bash
# Emulator walk through the 1.0 touches: the welcome asking what you read, undo instead of
# confirmations, the detail page, one search for everything, and the yearly goal.
set -x
APP=io.github.christiandonatti.fwip
OUT=repro
mkdir -p $OUT
exec > >(tee $OUT/log.txt) 2>&1
W=1920; H=1200
note() { echo "::notice::$*"; }
shot() {
  sleep "${2:-2}"
  timeout 40 adb shell screencap -p /sdcard/shot.png
  timeout 40 adb pull /sdcard/shot.png "$OUT/$1.png" > /dev/null
  note "$1"
}
ui() { timeout 60 python3 scripts/ui.py "$1"; }
find_xy() { timeout 60 python3 -c "import sys; sys.path.insert(0,'scripts'); import ui; p=ui.find(sys.argv[1]); print(f'{p[0]} {p[1]}' if p else '')" "$1"; }
restart() { timeout 20 adb shell am force-stop $APP; timeout 20 adb shell am start -n $APP/.MainActivity; sleep "${1:-12}"; }
put() { timeout 20 adb exec-in "run-as $APP sh -c 'cat > $2'" < "$1"; }
back() { timeout 20 adb shell input keyevent KEYCODE_BACK; sleep "${1:-2}"; }
longpress() { local xy; xy=$(find_xy "$1"); [ -n "$xy" ] && { set -- $xy; timeout 20 adb shell input swipe $1 $2 $1 $2 1200; }; }
crashes() { timeout 20 adb logcat -d -b crash > "$OUT/crash-$1.txt"; [ -s "$OUT/crash-$1.txt" ] && note "CRASH after $1: $(head -c 600 $OUT/crash-$1.txt | tr '\n' ' ')"; timeout 10 adb logcat -c -b crash; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell wm size ${W}x${H}
adb shell wm density 240
timeout 10 adb logcat -c -b crash

# ---- A new install: the welcome ----
adb shell pm clear $APP
restart 15
shot u01-welcome 2
ui '^Continue\|' ; shot u02-kind 2
ui '^Books\|' ; shot u03-look 2
ui '^Continue\|' ; shot u04-add-books 2
crashes welcome

# ---- Someone with comics and books ----
adb shell pm clear $APP
adb shell "run-as $APP mkdir -p files/Baixadas shared_prefs"
python3 scripts/look_prefs.py welcomed=true seenVersion=i:999999 > /tmp/ui.xml
for k in 1 2 3; do put /tmp/ui.xml shared_prefs/fwip_ui.xml; adb shell "run-as $APP cat shared_prefs/fwip_ui.xml" | grep -q welcomed && break; done
for f in comics/PepperCarrot_0[1-4].cbz books/Quiet_Street_1.epub books/Quiet_Street_2.epub books/The_Bicycle.fb2; do put "$f" "files/Baixadas/$(basename "$f")"; done
restart 30
shot u05-home 2
ui '^Library\|' ; sleep 2
longpress 'Pepper'
shot u06-detail 3
xy=$(find_xy '^Delete file\|'); [ -z "$xy" ] && { timeout 20 adb shell input swipe $(( W / 2 )) $(( H - 150 )) $(( W / 2 )) 200 500; sleep 1; }
ui '^Delete file\|' ; shot u07-undo-bar 1
ui '^Undo\|' ; shot u08-undone 2
crashes undo

# ---- One search ----
ui '\|Search$' ; sleep 1
timeout 20 adb shell input text "quiet"; shot u09-search-books 2
timeout 20 adb shell input keyevent KEYCODE_ESCAPE; sleep 1
xy=$(find_xy '\|Close search$'); [ -n "$xy" ] && { set -- $xy; timeout 20 adb shell input tap $1 $2; sleep 1; }
ui '\|Search$' ; sleep 1
timeout 20 adb shell input text "warm"; shot u10-search-settings 2
timeout 20 adb shell input keyevent KEYCODE_ESCAPE; sleep 1
crashes search

# ---- Yearly goal ----
restart 15
timeout 20 adb shell am start -n $APP/.MainActivity -a $APP.SHOW --es screen stats; sleep 4
shot u11-stats 2
ui '^Set a goal\|' ; sleep 1
for k in 1 2 3 4; do ui '^\+\|' ; done
shot u12-goal-dialog 1
ui '^Save\|' ; shot u13-goal 2
crashes goal

# ---- Phone ----
adb shell wm size 1080x2400
adb shell wm density 420
restart 20
shot u14-phone-home 2
ui '^Library\|' ; sleep 2
longpress 'Quiet'
shot u15-phone-detail 3
crashes phone
note done
