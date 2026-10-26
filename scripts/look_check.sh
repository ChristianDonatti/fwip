#!/bin/bash
# Emulator walk through 0.8: page strip, list view, progress styles, publishers, start tab,
# reader gestures and every widget (hosted by the test-only WidgetPreviewActivity).
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
openc() { timeout 20 adb shell am start -n $APP/.MainActivity -a $APP.OPEN_COMIC --es comic "file:///data/user/0/$APP/files/Baixadas/$1"; sleep "${2:-8}"; }
swipe_up() { timeout 20 adb shell input swipe $(( W / 2 )) $(( H * 3 / 4 )) $(( W / 2 )) $(( H / 4 )) 500; sleep 1; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell svc power stayon true
adb shell wm size ${W}x${H}
adb shell wm density 240
adb shell pm clear $APP
adb shell "run-as $APP mkdir -p files/Baixadas shared_prefs"
python3 scripts/look_prefs.py welcomed=true > /tmp/ui.xml
for k in 1 2 3; do put /tmp/ui.xml shared_prefs/fwip_ui.xml; adb shell "run-as $APP cat shared_prefs/fwip_ui.xml" | grep -q welcomed && break; done
for f in comics/PepperCarrot_0[1-8].cbz; do put "$f" "files/Baixadas/$(basename $f)"; done
restart 25
# Some reading, for progress, stats and "up next".
openc PepperCarrot_01.cbz
for k in 1 2 3 4 5 6; do timeout 20 adb shell input tap $(( W - 60 )) $(( H / 2 )); sleep 1.5; done
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 2
openc PepperCarrot_02.cbz
for k in 1 2; do timeout 20 adb shell input tap $(( W - 60 )) $(( H / 2 )); sleep 1.5; done
# The page bar as a strip of pages.
timeout 20 adb shell input tap $(( W / 2 )) $(( H / 2 ))
shot r1-strip 2
xy=$(find_xy '\|Pages$')
if [ -n "$xy" ]; then
  set -- $xy
  bx=$(( $1 + 200 )); by=$2
  timeout 20 adb shell input motionevent DOWN $bx $by
  for k in 1 2 3 4 5; do timeout 20 adb shell input motionevent MOVE $(( bx + 120 * k )) $by; done
  shot r2-strip-drag 1
  timeout 20 adb shell input motionevent UP $(( bx + 600 )) $by
fi
timeout 20 adb shell input tap $(( W / 2 )) $(( H / 2 )); sleep 1
# Swipe up: page overview (the default).
timeout 20 adb shell input swipe $(( W / 2 )) $(( H * 3 / 4 )) $(( W / 2 )) $(( H / 5 )) 250
shot r3-swipe-up 2
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 1
timeout 20 adb shell input tap $(( W / 2 )) $(( H / 2 )); sleep 1
ui '\|Reading settings$' ; sleep 2
for k in 1 2 3; do timeout 20 adb shell input swipe $(( W / 2 )) $(( H * 3 / 4 )) $(( W / 2 )) $(( H / 3 )) 500; sleep 1; done
shot r4-gestures 1
for k in 1 2; do timeout 20 adb shell input swipe $(( W / 2 )) $(( H * 3 / 4 )) $(( W / 2 )) $(( H / 3 )) 500; sleep 1; done
shot r5-look-turn 1
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 1
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 3

# ---- Library: list view, publishers, progress ring ----
ui '^Library\|' ; sleep 2
ui '^List\|' ; shot l1-list 2
ui '^Covers\|' ; sleep 1
ui '^Publishers\|' ; shot l2-publishers 2
ui '^All\|' ; sleep 1
timeout 20 adb shell am force-stop $APP
python3 scripts/look_prefs.py welcomed=true progressStyle=Ring startTab=Library tabServers=false > /tmp/ui.xml
put /tmp/ui.xml shared_prefs/fwip_ui.xml
restart 15
shot l3-ring-start-library 2
python3 scripts/look_prefs.py welcomed=true progressStyle=Percent > /tmp/ui.xml
timeout 20 adb shell am force-stop $APP; put /tmp/ui.xml shared_prefs/fwip_ui.xml; restart 15
ui '^Library\|' ; shot l4-percent 2
ui '\|Settings$' ; sleep 1
ui '^Appearance\|' ; sleep 2
ui '^Home\|' ; shot a1-home-tab 2
ui '^Covers\|' ; sleep 1; swipe_up; shot a2-covers-tab 1
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 2

# ---- A collection for its widget (reading list import) ----
cat > /tmp/event.cbl <<'X'
<?xml version="1.0"?>
<ReadingList><Name>Pepper Saga</Name><Books>
<Book Series="Pepper&amp;Carrot" Number="1" /><Book Series="Pepper&amp;Carrot" Number="2" />
<Book Series="Pepper&amp;Carrot" Number="3" /><Book Series="Pepper&amp;Carrot" Number="4" />
<Book Series="Pepper&amp;Carrot" Number="5" /><Book Series="Pepper&amp;Carrot" Number="6" />
</Books></ReadingList>
X
adb push /tmp/event.cbl /sdcard/Download/event.cbl
ui '^Collections\|' ; sleep 2
ui '^Import a reading list\|' ; sleep 4
if [ -z "$(find_xy '^event.cbl\|')" ]; then ui '\|Show roots$' ; sleep 2; ui '^Downloads\|' ; sleep 3; fi
ui '^event.cbl\|' ; sleep 4
ui '^Create collection\|' ; sleep 3
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 2

# ---- Widgets ----
adb shell appwidget grantbind --package $APP --user 0
timeout 20 adb shell am start -n $APP/.WidgetPreviewActivity
sleep 14
shot g1-widgets 2
for k in 1 2 3 4 5; do timeout 20 adb shell input swipe $(( W / 2 )) $(( H * 4 / 5 )) $(( W / 2 )) $(( H / 5 )) 600; shot g$(( k + 1 ))-widgets 2; done
# Phone-ish narrow screen for the widgets too.
timeout 20 adb shell am force-stop $APP
adb shell wm size 1080x2400
adb shell wm density 420
timeout 20 adb shell am start -n $APP/.WidgetPreviewActivity
sleep 14
shot q1-widgets-phone 2
for k in 1 2 3 4; do timeout 20 adb shell input swipe 540 1900 540 500 600; shot q$(( k + 1 ))-widgets-phone 2; done

adb shell wm size reset
adb shell wm density reset
timeout 20 adb logcat -d > /tmp/log.txt
grep " Fwip \|AppWidget\|Glance" /tmp/log.txt | tail -80 > $OUT/fwip-log.txt
timeout 30 adb logcat -d -b crash > $OUT/crash.txt
note "crashes: $(head -c 800 $OUT/crash.txt | tr '\n' ' ')"
