#!/bin/bash
# The in-app update, end to end: an old test build finds the newest test build on GitHub,
# downloads it, asks Android once for the right to install, and installs it over itself.
set -x
APP=io.github.christiandonatti.fwip
OUT=repro
mkdir -p $OUT
exec > >(tee $OUT/log.txt) 2>&1
note() { echo "::notice::$*"; }
shot() { sleep "${2:-2}"; timeout 40 adb shell screencap -p /sdcard/shot.png; timeout 40 adb pull /sdcard/shot.png "$OUT/$1.png" > /dev/null; note "$1"; }
ui() { timeout 60 python3 scripts/ui.py "$1"; }
put() { timeout 20 adb exec-in "run-as $APP sh -c 'cat > $2'" < "$1"; }
version() { adb shell dumpsys package $APP | grep -m1 versionCode | tr -s ' '; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell pm clear $APP
adb shell "run-as $APP mkdir -p shared_prefs"
python3 scripts/look_prefs.py welcomed=true seenVersion=i:999999 > /tmp/ui.xml
for k in 1 2 3; do put /tmp/ui.xml shared_prefs/fwip_ui.xml; adb shell "run-as $APP cat shared_prefs/fwip_ui.xml" | grep -q welcomed && break; done
note "before: $(version)"
timeout 20 adb shell am start -n $APP/.MainActivity
shot u01-banner 25
ui '^Update\|' && note "tapped Update"
for k in 1 2 3 4 5 6; do shot u02-download-$k 3; done
# Android's permission, once: the app sends you to the page, you allow it, you come back.
for k in $(seq 1 30); do ui '^Allow\|' > /dev/null && break; sleep 3; done
shot u03-allow-page 3
adb shell appops set $APP REQUEST_INSTALL_PACKAGES allow
timeout 20 adb shell input keyevent KEYCODE_BACK
shot u04-back 3
shot u05-confirm 4
ui '^Update\|^UPDATE\|^Install\|^INSTALL' && note "confirmed"
shot u06-installing 4
for k in $(seq 1 20); do sleep 3; v=$(version); echo "$v" | grep -q 'versionCode=5 ' || break; done
note "after: $(version)"
timeout 20 adb shell am start -n $APP/.MainActivity
shot u07-after 8
note done
