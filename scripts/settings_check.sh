#!/bin/bash
# Emulator walk through 0.9: what's new, the new Settings (tablet two panes and phone), search,
# light and comfort, e-ink themes and pages, the reader's quick panel, keys, and the screen saver.
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
swipe_up() { timeout 20 adb shell input swipe $(( W * 2 / 3 )) $(( H * 3 / 4 )) $(( W * 2 / 3 )) $(( H / 4 )) 500; sleep 1; }
back() { timeout 20 adb shell input keyevent KEYCODE_BACK; sleep "${1:-2}"; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell svc power stayon true
adb shell wm size ${W}x${H}
adb shell wm density 240
adb shell pm clear $APP
adb shell "run-as $APP mkdir -p files/Baixadas shared_prefs"
# An existing user (welcomed, comics) who has not seen the 0.9 news yet.
python3 scripts/look_prefs.py welcomed=true > /tmp/ui.xml
for k in 1 2 3; do put /tmp/ui.xml shared_prefs/fwip_ui.xml; adb shell "run-as $APP cat shared_prefs/fwip_ui.xml" | grep -q welcomed && break; done
for f in comics/PepperCarrot_0[1-6].cbz; do put "$f" "files/Baixadas/$(basename $f)"; done
restart 25
shot n1-whats-new 2
ui '^Got it\|' ; sleep 2
openc PepperCarrot_01.cbz
for k in 1 2 3; do timeout 20 adb shell input tap $(( W - 60 )) $(( H / 2 )); sleep 1.5; done
back 3

# ---- Settings on the tablet: two panes ----
ui '\|Settings$' ; shot s1-settings 3
ui '^Light and comfort\|' ; shot s2-light 2
swipe_up; shot s3-light-more 1
swipe_up; shot s4-light-end 1
ui '^Comic reading\|' ; shot s5-comics 2
# Search finds one setting and lights it up.
ui '^Search settings\|' ; sleep 1
timeout 20 adb shell input text "warm"
shot s6-search 2
ui '^Warm light schedule\|' ; shot s7-found 1
timeout 20 adb shell input keyevent KEYCODE_ESCAPE; sleep 1
xy=$(find_xy '\|Close search$'); if [ -n "$xy" ]; then set -- $xy; timeout 20 adb shell input tap $1 $2; sleep 1; fi
ui '^Appearance\|' ; shot s8-appearance 2
ui '^E-ink\|' ; shot s9-ink-theme 3
ui '^Notices and widgets\|' ; shot s10-alerts 2
ui '^Sync and backup\|' ; sleep 1; swipe_up; shot s11-sync 1
ui '^About and help\|' ; shot s12-about 2
back 2
shot l1-library-ink 3

# ---- E-ink pages, clock and the quick panel ----
ui '\|Settings$' ; sleep 2
ui '^Light and comfort\|' ; sleep 2
ui '^E-ink\|' ; sleep 1
swipe_up
ui '^Clock\|' ; sleep 1
ui '^Battery\|' ; sleep 1
back 2
openc PepperCarrot_02.cbz 10
shot r1-ink-page 2
timeout 20 adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1
timeout 20 adb shell input keyevent KEYCODE_DPAD_RIGHT
shot r2-arrows 2
timeout 20 adb shell input tap $(( W / 2 )) $(( H / 2 )); sleep 1
ui '\|Reading settings$' ; shot r3-quick 2
ui '^Colour e-ink\|' ; sleep 1
ui '^More settings\|' ; shot r4-more 2
ui '^Light and comfort\|' ; shot r5-more-light 2
back 1; back 1; sleep 3
shot r6-colour-ink 2
back 3

# ---- Phone: one pane, back goes to the list ----
timeout 20 adb shell am force-stop $APP
adb shell wm size 1080x2400
adb shell wm density 420
restart 15
ui '\|Settings$' ; shot p1-settings-phone 2
ui '^Light and comfort\|' ; shot p2-light-phone 2
back 2
shot p3-back-to-list 1
back 2
adb shell wm size ${W}x${H}
adb shell wm density 240

# ---- Screen saver with the cover ----
adb shell settings put secure screensaver_enabled 1
adb shell settings put secure screensaver_components $APP/.widget.CoverDream
timeout 20 adb shell am start -n com.android.systemui/.Somnambulator || timeout 20 adb shell cmd dreams start-dreaming
shot d1-dream 8
timeout 20 adb shell input keyevent KEYCODE_WAKEUP; sleep 1

timeout 20 adb logcat -d > /tmp/log.txt
grep " Fwip \|AndroidRuntime" /tmp/log.txt | tail -80 > $OUT/fwip-log.txt
timeout 30 adb logcat -d -b crash > $OUT/crash.txt
note "crashes: $(head -c 800 $OUT/crash.txt | tr '\n' ' ')"
