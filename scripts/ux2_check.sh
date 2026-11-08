#!/bin/bash
# The 1.2 experience work: landscape welcome, reader tips once, end card with the series,
# up next on Home, generated covers, swipe on list rows, friendly error with retry.
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
labels() { timeout 60 python3 scripts/ui.py '^@@nothing@@$' 2>&1 | head -c 1500; echo; }
home() { for k in 1 2 3; do has '^Home\|' && return; back 2; done; }
crashes() { timeout 20 adb logcat -d -b crash > "$OUT/crash-$1.txt"; [ -s "$OUT/crash-$1.txt" ] && note "CRASH after $1: $(head -c 600 $OUT/crash-$1.txt | tr '\n' ' ')"; timeout 10 adb logcat -c -b crash; }
prefs() { python3 scripts/look_prefs.py "$@" > /tmp/ui.xml; for k in 1 2 3; do put /tmp/ui.xml shared_prefs/fwip_ui.xml; adb shell "run-as $APP cat shared_prefs/fwip_ui.xml" | grep -q welcomed && break; done; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell wm size ${W}x${H}
adb shell wm density 240
timeout 10 adb logcat -c -b crash

# ---- Welcome on a tablet lying down: one screen, nothing to scroll ----
adb shell pm clear $APP
restart 15
shot a01-welcome-landscape 1
crashes welcome

# ---- A library, tips not seen yet ----
adb shell pm clear $APP
adb shell "run-as $APP mkdir -p files/Baixadas shared_prefs"
prefs welcomed=true seenVersion=i:999999
for f in comics/PepperCarrot_0*.cbz books/Quiet_Street_1.epub books/Quiet_Street_2.epub books/The_Bicycle.fb2; do
  [ -f "$f" ] && put "$f" "files/Baixadas/$(basename "$f")"
done
restart 35
shot b01-home 1
labels

# Comic: tips the first time, then read to the end. The first issue, from the Library (sorted by name).
ui '^Library\|' ; sleep 2
ui 'Pepper' ; sleep 6
shot c01-comic-tips 1
tap $(( W / 2 )) $(( H / 2 )); sleep 2
shot c02-comic-after-tips 1
for k in $(seq 1 14); do tap $(( W - 100 )) $(( H / 2 )); sleep 1.2; has 'of .* read in|You have read all|Tap outside to stay' && break; done
shot c03-comic-end 1
labels
back 2; back 2
ui '^Home\|' ; sleep 2
crashes comic
shot b02-home-up-next 2
labels
# The same reader again: no tips this time.
ui '^Start\||^Continue\|' ; sleep 6
shot c04-comic-no-tips 1
home

# The broken file: a friendly message, and what to do.
head -c 40000 /dev/urandom > /tmp/Broken.cbz; put /tmp/Broken.cbz files/Baixadas/Broken_Issue.cbz
ui '\|Refresh library$' ; sleep 6
ui '^Library\|' ; sleep 2
ui '^Search\||\|Search$' ; sleep 1
timeout 20 adb shell input text "Broken"; sleep 2
timeout 20 adb shell input keyevent 111; sleep 1
shot e00-search-broken 1
ui 'Broken Issue' ; sleep 5
shot e01-error 1
labels
home
crashes error

# ---- List rows that swipe ----
adb shell am force-stop $APP
prefs welcomed=true comicTips=true seenVersion=i:999999 libraryList=true
# Books turn instantly here: many paper turns in a row with the screen being read take the emulator down.
python3 scripts/look_prefs.py turn=Instant > /tmp/books.xml; put /tmp/books.xml shared_prefs/fwip_books.xml
restart 20
ui '^Library\|' ; sleep 2
shot f01-library-list 1
xy=$(find_xy 'Pepper'); if [ -n "$xy" ]; then set -- $xy
  timeout 20 adb shell input swipe 300 $2 900 $2 500; shot f02-swiped-right 1
  labels
  sleep 6
  timeout 20 adb shell input swipe 1200 $2 500 $2 500; shot f03-swiped-left 1
fi
crashes swipe

# Book: tips, then the last chapter and the end card.
ui '^Home\|' ; sleep 2
ui '^Books\|' ; sleep 2
shot b03-books-covers 2
ui '^Start\||^Continue\|' ; sleep 10
shot d01-book-tips 1
tap $(( W / 2 )) $(( H / 2 )); sleep 2
tap $(( W / 2 )) $(( H / 2 )); sleep 2
has '\|Contents$' || tap $(( W / 2 )) $(( H / 2 ))
ui '\|Contents$' ; sleep 2
ui '^Chapter 6\||^Chapter 3\|' ; sleep 4
for k in $(seq 1 40); do tap $(( W - 100 )) $(( H / 2 )); sleep 1.3; [ $(( k % 4 )) = 0 ] && has 'of .* read in|You have read all|Tap outside to stay' && break; done
shot d02-book-end 2
timeout 20 adb logcat -d -s FwipEnd | tail -8
labels
home
crashes book

# ---- Phone: welcome and home ----
adb shell wm size 1080x2400
adb shell wm density 420
restart 20
shot g01-phone-home 2
adb shell pm clear $APP
restart 15
shot g02-phone-welcome 1
crashes phone
note done
