#!/bin/bash
# Emulator walk through the 3D book: the cover opening, paper turns by tap and by finger,
# every scene drawn and as a photo, the scene panel, and a phone.
set -x
APP=io.github.christiandonatti.fwip
OUT=repro
mkdir -p $OUT
exec > >(tee $OUT/log.txt) 2>&1
W=1920; H=1200
D=/data/user/0/$APP/files/Baixadas
note() { echo "::notice::$*"; }
shot() {
  sleep "${2:-2}"
  timeout 40 adb shell screencap -p /sdcard/shot.png
  timeout 40 adb pull /sdcard/shot.png "$OUT/$1.png" > /dev/null
  note "$1"
}
quick() { timeout 40 adb shell screencap -p /sdcard/q.png; timeout 40 adb pull /sdcard/q.png "$OUT/$1.png" > /dev/null; note "$1"; }
ui() { timeout 60 python3 scripts/ui.py "$1"; }
put() { timeout 20 adb exec-in "run-as $APP sh -c 'cat > $2'" < "$1"; }
openb() { timeout 20 adb shell am start -n $APP/.MainActivity -a $APP.OPEN_COMIC --es comic "file://$D/$1"; sleep "${2:-10}"; }
tap() { timeout 20 adb shell input tap "$1" "$2"; sleep "${3:-1.5}"; }
back() { timeout 20 adb shell input keyevent KEYCODE_BACK; sleep "${1:-2}"; }
crashes() {
  timeout 20 adb logcat -d -b crash > "$OUT/crash-$1.txt"
  [ -s "$OUT/crash-$1.txt" ] && note "CRASH after $1: $(head -c 600 $OUT/crash-$1.txt | tr '\n' ' ')"
  timeout 10 adb logcat -c -b crash
  if ! timeout 10 adb shell pidof $APP > /dev/null; then
    note "APP GONE after $1"
    timeout 20 adb exec-out run-as $APP cat files/reports/last-crash.txt > "$OUT/gone-$1-report.txt" 2>/dev/null
    timeout 20 adb logcat -d -b main,system,crash -t 1500 > "$OUT/gone-$1-logcat.txt"
  fi
}
books() { python3 scripts/look_prefs.py "$@" > /tmp/books.xml; put /tmp/books.xml shared_prefs/fwip_books.xml; }
stopapp() { timeout 20 adb shell am force-stop $APP; sleep 1; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell wm size ${W}x${H}
adb shell wm density 240
adb shell pm clear $APP
adb shell "run-as $APP mkdir -p files/Baixadas shared_prefs"
python3 scripts/look_prefs.py welcomed=true seenVersion=i:999999 > /tmp/ui.xml
for k in 1 2 3; do put /tmp/ui.xml shared_prefs/fwip_ui.xml; adb shell "run-as $APP cat shared_prefs/fwip_ui.xml" | grep -q welcomed && break; done
for f in books/Quiet_Street_1.epub books/Dom_Casmurro.epub; do [ -f "$f" ] && put "$f" "files/Baixadas/$(basename "$f")"; done
books book3d=true scene=Fireplace ambientSound=false "photoScenes=set:Fireplace"
timeout 10 adb logcat -c -b crash
timeout 20 adb shell am start -n $APP/.MainActivity; sleep 30

# ---- Opening, turning ----
BOOK=Quiet_Street_1.epub
[ -f books/Dom_Casmurro.epub ] && BOOK=Dom_Casmurro.epub
timeout 20 adb shell am start -n $APP/.MainActivity -a $APP.OPEN_COMIC --es comic "file://$D/$BOOK"
sleep 1.2; quick t01-cover-closed
sleep 0.6; quick t02-cover-opening
shot t03-open 6
crashes open
# Turn by tap: catch it in the middle.
timeout 20 adb shell input tap $(( W - 120 )) $(( H / 2 )) & sleep 0.45; quick t04-turning; wait $!
shot t05-turned 2
# Turn by finger, held in the middle of the drag.
timeout 20 adb shell input swipe $(( W - 260 )) $(( H - 300 )) $(( W / 2 )) $(( H - 380 )) 1500 & sleep 0.9; quick t06-dragging; wait $!
shot t07-dragged 2
timeout 20 adb shell input tap 120 $(( H / 2 )) & sleep 0.45; quick t08-back-turning; wait $!
shot t09-back 2
crashes turns
tap $(( W / 2 )) $(( H / 2 ))
shot t10-chrome 1
ui '^3D\|' ; shot t11-scene-panel 2
timeout 20 adb shell input swipe $(( W / 2 )) $(( H - 150 )) $(( W / 2 )) $(( H / 3 )) 500; shot t12-scene-panel-more 1
back 2
crashes panel

# ---- Every scene, drawn and photo ----
for style in drawn photo; do
  for sc in Desk Library Fireplace Mountain Park Rain Beach Night; do
    stopapp
    if [ $style = photo ]; then ps="set:$sc"; else ps="set:"; fi
    books book3d=true scene=$sc ambientSound=false "photoScenes=$ps"
    openb $BOOK 9
    shot s-$style-$sc 1
    crashes $style-$sc
  done
done

# ---- Paper turns without the 3D book ----
stopapp
books book3d=false turn=Paper
openb $BOOK 9
timeout 20 adb shell input tap $(( W - 120 )) $(( H / 2 )) & sleep 0.45; quick p01-flat-turning; wait $!
shot p02-flat-turned 2
crashes flat

# ---- Phone ----
stopapp
adb shell wm size 1080x2400
adb shell wm density 420
books book3d=true scene=Park ambientSound=false "photoScenes=set:Park"
openb $BOOK 10
shot f01-phone-3d 3
timeout 20 adb shell input tap 980 1200 & sleep 0.45; quick f02-phone-turning; wait $!
shot f03-phone-turned 2
tap 540 1200
shot f04-phone-chrome 1
ui '\|More options$' ; shot f05-phone-menu 1
back 2
crashes phone
note done
