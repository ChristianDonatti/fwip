#!/bin/bash
# A walk through every screen as a person would use them, on a tablet lying down and on a phone,
# to look at the experience as a whole (layout, wording, empty states, flow between screens).
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
has() { timeout 60 python3 -c "import sys; sys.path.insert(0, 'scripts'); import ui; sys.exit(0 if ui.find(sys.argv[1]) else 1)" "$1"; }
find_xy() { timeout 60 python3 -c "import sys; sys.path.insert(0,'scripts'); import ui; p=ui.find(sys.argv[1]); print(f'{p[0]} {p[1]}' if p else '')" "$1"; }
restart() { timeout 20 adb shell am force-stop $APP; timeout 20 adb shell am start -n $APP/.MainActivity; sleep "${1:-12}"; }
put() { timeout 20 adb exec-in "run-as $APP sh -c 'cat > $2'" < "$1"; }
back() { timeout 20 adb shell input keyevent KEYCODE_BACK; sleep "${1:-2}"; }
tap() { timeout 20 adb shell input tap "$1" "$2"; }
longpress() { local xy; xy=$(find_xy "$1"); [ -n "$xy" ] && { set -- $xy; timeout 20 adb shell input swipe $1 $2 $1 $2 1200; }; }
crashes() { timeout 20 adb logcat -d -b crash > "$OUT/crash-$1.txt"; [ -s "$OUT/crash-$1.txt" ] && note "CRASH after $1: $(head -c 600 $OUT/crash-$1.txt | tr '\n' ' ')"; timeout 10 adb logcat -c -b crash; }
D=/data/data/$APP/files/Baixadas

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell wm size ${W}x${H}
adb shell wm density 240
timeout 10 adb logcat -c -b crash

# ---- First run ----
adb shell pm clear $APP
restart 15
shot a01-welcome 1
ui '^Continue\|' ; shot a02-welcome-kind 1
ui '^Both\|' ; shot a03-welcome-look 1
ui '^Continue\|' ; shot a04-welcome-folder 1
ui '^Continue\|^Skip\|^Later\|^Not now\|' ; shot a05-after-welcome 2
crashes welcome

# ---- A reader with a library ----
adb shell pm clear $APP
adb shell "run-as $APP mkdir -p files/Baixadas shared_prefs"
python3 scripts/look_prefs.py welcomed=true comicTips=true bookTips=true seenVersion=i:999999 > /tmp/ui.xml
for k in 1 2 3; do put /tmp/ui.xml shared_prefs/fwip_ui.xml; adb shell "run-as $APP cat shared_prefs/fwip_ui.xml" | grep -q welcomed && break; done
for f in comics/PepperCarrot_0*.cbz comics/*.cbz books/Quiet_Street_1.epub books/Quiet_Street_2.epub books/The_Bicycle.fb2 books/Dom_Casmurro.epub; do
  [ -f "$f" ] && put "$f" "files/Baixadas/$(basename "$f")"
done
restart 35
shot b01-home-first 1
labels() { timeout 60 python3 scripts/ui.py '^@@nothing@@$' 2>&1 | head -c 1500; echo; }
# Read a little: a comic and a book, so "continue reading" and progress show.
ui '^Start\|' ; sleep 6
shot c01-comic-open 1
tap $(( W - 100 )) $(( H / 2 )); sleep 2; tap $(( W - 100 )) $(( H / 2 )); sleep 2
tap $(( W / 2 )) $(( H / 2 )); shot c02-comic-chrome 2
labels
ui '\|Reading settings$\|\|Settings$\|\|Quick settings$' ; shot c03-comic-quick-settings 2
back 1
has '\|Pages$' || tap $(( W / 2 )) $(( H / 2 ))
ui '\|Pages$' ; shot c04-comic-pages 3
back 1
has '^Home\|' || back 2
has '^Home\|' || back 2
shot b02-home-after-comic 2
ui '^Books\|' ; sleep 2; shot b03-home-books 1
ui '^Start\|^Continue\|' ; sleep 10
shot d01-book-open 1
tap $(( W - 100 )) $(( H / 2 )); sleep 2
tap $(( W / 2 )) $(( H / 2 )); shot d02-book-chrome 2
labels
ui '^Aa\|\|Text settings$\|\|Text$' ; shot d03-book-aa 2
back 1
has '\|Contents$' || tap $(( W / 2 )) $(( H / 2 ))
ui '\|Contents$' ; shot d04-book-contents 2
back 1
has '^Home\|' || back 2
has '^Home\|' || back 2
crashes readers
shot b04-home-books-after 2
ui '^Comics\|' ; sleep 2
shot b05-home 2
timeout 20 adb shell input swipe $(( W / 2 )) $(( H - 200 )) $(( W / 2 )) 300 600; shot b06-home-scrolled 2
timeout 20 adb shell input swipe $(( W / 2 )) $(( H - 200 )) $(( W / 2 )) 300 600; shot b07-home-scrolled-more 2
ui '^Library\|' ; shot b08-library 2
ui '^Collections\|' ; shot b09-collections 2
ui '^Servers\|' ; shot b10-servers 2
ui '^Home\|' ; sleep 2
longpress 'Pepper' ; shot b11-detail 3
labels
back 2
ui '\|Search$' ; shot b12-search-empty 2
timeout 20 adb shell input text "pepper"; shot b13-search-results 2
back 1; has '^Home\|' || back 2
crashes library

# ---- Settings ----
ui '\|Settings$' ; shot e01-settings 2
labels
for g in 'Appearance' 'Comics' 'Books' 'Library' 'Light' 'Sync' 'Backup' 'About'; do
  ui "^$g" && shot e-$g 2
done
back 2; has '^Home\|' || back 2
crashes settings

# ---- Selection, stats ----
ui '^Library\|' ; sleep 2
longpress 'Pepper' ; shot f01-selection 2
labels
back 2
ui '^Home\|' ; sleep 2
for k in 1 2 3; do timeout 20 adb shell input swipe $(( W / 2 )) $(( H - 200 )) $(( W / 2 )) 300 400; sleep 1; done
ui 'last 7 days\|' ; shot f02-stats 3
back 2
crashes misc

# ---- Phone ----
adb shell wm size 1080x2400
adb shell wm density 420
restart 20
shot g01-phone-home 2
timeout 20 adb shell input swipe 540 1900 540 600 600; shot g02-phone-home-scrolled 2
ui '^Library\|' ; shot g03-phone-library 2
ui '^Home\|' ; sleep 2
longpress 'Pepper' ; shot g04-phone-detail 3
back 2
ui '^Continue\|^Start\|' ; sleep 8
shot g05-phone-comic 1
tap 540 1200; shot g06-phone-comic-chrome 2
back 1; has '^Home\|' || back 2
ui '\|Settings$' ; shot g08-phone-settings 2
crashes phone
note done
