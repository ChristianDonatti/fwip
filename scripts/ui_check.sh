#!/bin/bash
# Walks through the library look on an emulator: welcome, themes, accents, the Appearance screen,
# carousels, home parts, covers, icons and the reader's look. Screenshots go to repro/.
set -x
APP=io.github.christiandonatti.fwip
OUT=repro
mkdir -p $OUT
W=1920; H=1200
note() { echo "::notice::$*"; }
note "ui_check start"

shot() {
  sleep "${2:-2}"
  timeout 40 adb shell screencap -p /sdcard/shot.png
  timeout 40 adb pull /sdcard/shot.png "$OUT/$1.png" > /dev/null
  note "$1"
}
quick() {
  timeout 20 adb shell screencap -p /sdcard/q.png
  timeout 20 adb pull /sdcard/q.png "$OUT/$1.png" > /dev/null
}
ui() { timeout 60 python3 scripts/ui.py "$1"; }
find_xy() { timeout 60 python3 -c "import sys; sys.path.insert(0,'scripts'); import ui; p=ui.find(sys.argv[1]); print(f'{p[0]} {p[1]}' if p else '')" "$1"; }
restart() { timeout 20 adb shell am force-stop $APP; timeout 20 adb shell am start -n $APP/.MainActivity; sleep "${1:-12}"; }
# Writes the look settings (key=value ...) and restarts the app.
look() {
  timeout 20 adb shell am force-stop $APP
  python3 scripts/look_prefs.py welcomed=true "$@" > /tmp/fwip_ui.xml
  timeout 20 adb exec-in "run-as $APP sh -c 'mkdir -p shared_prefs; cat > shared_prefs/fwip_ui.xml'" < /tmp/fwip_ui.xml
  restart 14
}
swipe_up() { timeout 20 adb shell input swipe $(( W / 2 )) $(( H * 3 / 4 )) $(( W / 2 )) $(( H / 4 )) 500; sleep 1; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell svc power stayon true
adb shell wm size ${W}x${H}
adb shell wm density 240

# ---- First launch: welcome and the theme picker ----
adb shell pm clear $APP
restart 12
shot w1-welcome 2
ui '^Continue\|' ; shot w2-look 2
ui '^Paper\|' ; shot w2-paper 2
ui '\|Ink blue$' ; shot w2-paper-blue 2
ui '^Continue\|' ; ui '^Later\|' ; sleep 2

# ---- Comics in ----
adb shell "run-as $APP mkdir -p files/Baixadas"
for f in comics/PepperCarrot_*.cbz; do
  b=$(basename "$f")
  adb exec-in "run-as $APP sh -c 'cat > files/Baixadas/$b'" < "$f"
done
restart 25
shot l1-home-paper 3

# Mark the first comic read, for the read marks.
ui '^Library\|' ; sleep 2
xy=$(find_xy '^Pepper&Carrot #1')
if [ -n "$xy" ]; then
  set -- $xy
  timeout 20 adb shell input swipe $1 $(( $2 - 150 )) $1 $(( $2 - 150 )) 900; sleep 2
  ui '^Mark as read\|' ; sleep 2
fi

# ---- Appearance screen, every tab ----
ui '\|Settings$' ; sleep 1.5
ui '^Appearance\|' ; shot a1-style 2
swipe_up ; shot a2-style-more 1
swipe_up ; shot a3-style-end 1
ui '^Covers\|' ; shot a4-covers 2
swipe_up ; shot a5-covers-more 1
ui '^Home\|' ; shot a6-home-parts 2
ui '^Icon\|' ; shot a7-icon 2
ui '^Night\|' ; sleep 3
timeout 30 adb shell dumpsys package $APP | grep -A30 "enabledComponents" | head -12 > $OUT/icons.txt
note "icon: $(grep -o 'Icon_[a-z_0-9]*' $OUT/icons.txt | tr '\n' ' ')"
ui '^Reader\|' ; shot a8-reader 2
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- Looks ----
look preset=Night titleFont=Unbounded carousel=Tilt name=Christian 'sections=[{"part":"Greeting","on":true},{"part":"Carousel","on":true},{"part":"Shelf","on":true},{"part":"Continue","on":true},{"part":"Week","on":true},{"part":"Want","on":true},{"part":"Recent","on":true}]'
shot b1-tilt-greeting 3
timeout 20 adb shell input swipe $(( W * 3 / 4 )) $(( H / 2 )) $(( W / 2 )) $(( H / 2 )) 300
shot b2-tilt-swiped 2
look preset=Comic titleFont=Comic carousel=Fan accentMode=Theme
shot b3-comic-fan 3
look preset=Manga titleFont=Unbounded corners=Square readMark=Ribbon coverTitles=false coverShadow=false
ui '^Library\|' ; shot b4-manga-library 3
look preset=Oled titleFont=Unbounded backdrop=Living readMark=Faded
ui '^Library\|' ; shot b5-oled-living 3
look preset=Night titleFont=Classic showCarousel=false 'sections=[{"part":"Series","on":true,"ref":"pepper carrot","title":"Pepper&Carrot"},{"part":"Continue","on":true},{"part":"Week","on":true},{"part":"Shelf","on":true},{"part":"Recent","on":true}]'
shot b6-no-carousel 3
swipe_up ; shot b7-no-carousel-more 1
look preset=Night titleFont=Unbounded accentMode=Cover corners=Round
shot b8-follow-cover 3
timeout 20 adb shell input tap $(( W * 70 / 100 )) $(( H * 40 / 100 )) ; shot b9-follow-cover-2 3

# ---- Reader look: paper ground and texture ----
timeout 20 adb shell am force-stop $APP
python3 scripts/look_prefs.py ground=Paper texture=true spine=0.9 > /tmp/fwip_reader.xml
timeout 20 adb exec-in "run-as $APP sh -c 'cat > shared_prefs/fwip_reader.xml'" < /tmp/fwip_reader.xml
restart 14
ui '^Library\|' ; sleep 2
xy=$(find_xy '^Pepper&Carrot #2')
if [ -n "$xy" ]; then
  set -- $xy
  timeout 20 adb shell input tap $1 $(( $2 - 150 )); sleep 6
  shot r1-reader-paper 2
  x0=$(( W * 96 / 100 )); y0=$(( H * 90 / 100 ))
  timeout 20 adb shell input motionevent DOWN $x0 $y0
  for k in 1 2 3 4 5; do timeout 20 adb shell input motionevent MOVE $(( x0 - (W * 9 / 100) * k )) $(( y0 - (H * 6 / 100) * k )); done
  shot r2-curl-texture 1
  timeout 20 adb shell input motionevent UP $(( x0 - (W * 45 / 100) )) $(( y0 - (H * 30 / 100) ))
  sleep 2
  # Feedback log for a couple of tap turns.
  timeout 10 adb logcat -c
  timeout 20 adb shell input tap $(( W - 60 )) $(( H / 2 )); sleep 2
  timeout 20 adb shell input tap $(( W - 60 )) $(( H / 2 )); sleep 2
  timeout 20 adb logcat -d > /tmp/log.txt
  grep -i "turn feedback" /tmp/log.txt > $OUT/feedback.txt
  note "feedback lines: $(wc -l < $OUT/feedback.txt) $(tail -1 $OUT/feedback.txt | cut -c1-160)"
  timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 2
fi

# ---- Tablet upright and phone ----
adb shell wm size 1200x1920
sleep 4
look preset=Paper titleFont=Classic carousel=Fan name=Christian 'sections=[{"part":"Greeting","on":true},{"part":"Carousel","on":true},{"part":"Continue","on":true},{"part":"Shelf","on":true},{"part":"Week","on":true},{"part":"Want","on":true},{"part":"Recent","on":true}]'
shot t1-upright-paper-fan 3
swipe_up ; shot t2-upright-more 1
adb shell wm size 1080x2400
adb shell wm density 420
sleep 4
look preset=Night titleFont=Unbounded name=Christian 'sections=[{"part":"Greeting","on":true},{"part":"Carousel","on":true},{"part":"Continue","on":true},{"part":"Week","on":true},{"part":"Shelf","on":true},{"part":"Recent","on":true}]'
shot p1-phone 3
timeout 20 adb shell input swipe 540 1800 540 600 500; sleep 1
shot p2-phone-more 1
adb shell wm size reset
adb shell wm density reset
timeout 30 adb logcat -d -b crash > $OUT/crash.txt
note "crashes: $(head -c 600 $OUT/crash.txt | tr '\n' ' ')"
ls -la $OUT
