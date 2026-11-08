#!/bin/bash
# Emulator walk through books: the comics/books switch, the EPUB reader (Aa panel, themes,
# contents, search, highlight, read aloud), TXT, FB2, MOBI, AZW3, a DRM book, Settings > Books.
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
ui() { timeout 60 python3 scripts/ui.py "$1"; }
find_xy() { timeout 60 python3 -c "import sys; sys.path.insert(0,'scripts'); import ui; p=ui.find(sys.argv[1]); print(f'{p[0]} {p[1]}' if p else '')" "$1"; }
restart() { timeout 20 adb shell am force-stop $APP; timeout 20 adb shell am start -n $APP/.MainActivity; sleep "${1:-12}"; }
put() { timeout 20 adb exec-in "run-as $APP sh -c 'cat > $2'" < "$1"; }
openb() { timeout 20 adb shell am start -n $APP/.MainActivity -a $APP.OPEN_COMIC --es comic "file://$D/$1"; sleep "${2:-10}"; }
tap() { timeout 20 adb shell input tap "$1" "$2"; sleep "${3:-1.5}"; }
back() { timeout 20 adb shell input keyevent KEYCODE_BACK; sleep "${1:-2}"; }
crashes() { timeout 20 adb logcat -d -b crash > "$OUT/crash-$1.txt"; [ -s "$OUT/crash-$1.txt" ] && note "CRASH after $1: $(head -c 600 $OUT/crash-$1.txt | tr '\n' ' ')"; timeout 10 adb logcat -c -b crash; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell svc power stayon true
adb shell wm size ${W}x${H}
adb shell wm density 240
adb shell pm clear $APP
adb shell "run-as $APP mkdir -p files/Baixadas shared_prefs"
python3 scripts/look_prefs.py welcomed=true comicTips=true bookTips=true seenVersion=i:999999 > /tmp/ui.xml
for k in 1 2 3; do put /tmp/ui.xml shared_prefs/fwip_ui.xml; adb shell "run-as $APP cat shared_prefs/fwip_ui.xml" | grep -q welcomed && break; done
for f in comics/PepperCarrot_0[1-3].cbz books/*; do put "$f" "files/Baixadas/$(basename "$f")"; done
adb shell "run-as $APP ls -la files/Baixadas"
timeout 10 adb logcat -c -b crash
restart 35
shot b01-library-comics 2
ui '^Books\|' ; shot b02-library-books 4
crashes library

# ---- EPUB ----
openb Quiet_Street_1.epub 12
shot b03-epub 2
crashes epub-open
tap $(( W / 2 )) $(( H / 2 )); shot b04-chrome 1
ui '^Aa\|' ; shot b05-aa 2
timeout 20 adb shell input swipe $(( W / 2 )) $(( H - 150 )) $(( W / 2 )) $(( H / 3 )) 500; shot b06-aa-more 1
timeout 20 adb shell input swipe $(( W / 2 )) $(( H / 3 )) $(( W / 2 )) $(( H - 150 )) 500; sleep 1
ui '^Night\|' ; shot b07-night 2
ui '^Paper\|' ; sleep 1
tap $(( W / 2 )) 60 ; sleep 1
# The controls are still up after a panel: hide them, then turn.
tap $(( W / 2 )) $(( H / 2 )) ; sleep 1
tap $(( W - 80 )) $(( H / 2 )); shot b08-next-page 2
tap $(( W - 80 )) $(( H / 2 )); sleep 1
tap $(( W / 2 )) $(( H / 2 ))
ui '\|Contents$' ; shot b09-contents 2
ui '^Chapter 4\|' ; shot b10-chapter4 3
tap $(( W / 2 )) $(( H / 2 ))
ui '\|Search in book$' ; sleep 2
timeout 20 adb shell input text "bicycle"; timeout 20 adb shell input keyevent KEYCODE_ENTER
shot b11-search 4
xy=$(find_xy 'Chapter 3'); if [ -n "$xy" ]; then set -- $xy; tap $1 $2; fi
shot b12-search-hit 3
# Select a word and highlight it.
timeout 20 adb shell input swipe $(( W / 3 )) $(( H / 3 )) $(( W / 3 )) $(( H / 3 )) 1500; shot b13-selection 2
ui '^Highlight\|' ; shot b14-highlight 2
tap $(( W / 2 )) $(( H / 2 ))
ui '\|Add bookmark$' ; shot b14b-bookmarked 1
ui '\|Contents$' ; sleep 2
ui '^Highlights\|' ; shot b15-highlights 2
tap $(( W / 2 )) 60 ; sleep 1
tap $(( W / 2 )) $(( H / 2 ))
tap $(( W / 2 )) $(( H / 2 ))
ui '^♪\|' ; shot b16-listen 2
ui '^Read aloud\|' ; shot b17-reading-aloud 5
ui '^Pause\|' ; sleep 1
tap $(( W / 2 )) 60 ; sleep 1
# Long press on a cover opens its page.
crashes epub
back 3
shot b18-library-after 2
ui '^Library\|' ; sleep 2
ui '^Shelf\|' ; shot b18b-spines 3
xy=$(find_xy 'Quiet Street'); if [ -n "$xy" ]; then set -- $xy; timeout 20 adb shell input swipe $1 $2 $1 $2 1200; fi
shot b18c-detail 3
back 2

# ---- Other formats ----
openb Evening_Notes.txt 12 ; shot b19-txt 2 ; crashes txt ; back 3
openb The_Bicycle.fb2 12 ; shot b20-fb2 2 ; crashes fb2 ; back 3
openb Libmobi_Textread.mobi 14 ; shot b21-mobi 2 ; crashes mobi
tap $(( W / 2 )) $(( H / 2 )); ui '\|Contents$' ; shot b22-mobi-contents 2 ; tap $(( W / 2 )) 60 ; back 3
openb Libmobi_Fonts.azw3 14 ; shot b23-azw3 2 ; crashes azw3 ; back 3
openb Libmobi_DRM.mobi 12 ; shot b24-drm 2 ; crashes drm ; back 3
[ -f books/Dom_Casmurro.epub ] && { openb Dom_Casmurro.epub 14 ; shot b25-gutenberg 2 ; crashes gutenberg ; back 3; }

# ---- Settings > Books ----
ui '\|Settings$' ; sleep 2
ui '^Books\|' ; shot b26-settings-books 2
back 2 ; back 2

# ---- Undo: delete a book, then undo ----
ui '^Home\|' ; sleep 2
ui '\|Settings$' ; sleep 2
ui '^Storage\|' ; sleep 2
xy=$(find_xy '\|Delete$'); if [ -n "$xy" ]; then set -- $xy; tap $1 $2; fi
shot b26b-undo 1
ui '^Undo\|' ; shot b26c-undone 2
back 2 ; back 2

# ---- Stats with a goal ----
ui '\|Settings$' ; sleep 2
ui '^Reading stats\|' ; sleep 2
ui '^Set a goal\|' ; sleep 1
for k in 1 2 3; do ui '^\+\|' ; done
shot b26d-goal-dialog 1
ui '^Save\|' ; shot b26e-stats 2
back 2 ; back 2

# ---- Search finds books and settings ----
ui '^Comics\|' ; sleep 1
ui '\|Search$' ; sleep 1
timeout 20 adb shell input text "quiet"; shot b26f-search 2
timeout 20 adb shell input keyevent KEYCODE_ESCAPE ; back 2

# ---- Phone ----
adb shell wm size 1080x2400
adb shell wm density 420
restart 20
shot b27-phone-library 2
openb Quiet_Street_2.epub 12 ; shot b28-phone-epub 2
tap 540 1200 ; shot b29-phone-chrome 1
ui '^Aa\|' ; shot b30-phone-aa 2
crashes phone
adb shell "run-as $APP ls -la files/books files/booknotes" || true
note done
