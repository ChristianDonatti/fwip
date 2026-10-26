#!/bin/bash
# Emulator walk through the 0.7 additions: new badge, storage, Wi-Fi transfer, reading lists,
# shelf looks, page filters and split double pages. Screenshots and notes go to repro/.
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
texts() { timeout 60 python3 -c "import sys; sys.path.insert(0,'scripts'); import ui; print('\n'.join((n.get('text') or '') for n in ui.nodes()))"; }
restart() { timeout 20 adb shell am force-stop $APP; timeout 20 adb shell am start -n $APP/.MainActivity; sleep "${1:-12}"; }
put() { timeout 20 adb exec-in "run-as $APP sh -c 'cat > $2'" < "$1"; }

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
for f in comics/PepperCarrot_0[1-6].cbz "comics/Repro Double.cbz"; do
  b=$(basename "$f" | tr ' ' '_')
  put "$f" "files/Baixadas/$b"
done
restart 25
shot h1-home 2

# ---- New badge: a comic that arrives after the first visit ----
put comics/PepperCarrot_07.cbz files/Baixadas/PepperCarrot_07.cbz
restart 20
ui '^Library\|' ; shot h2-new-badge 3

# ---- Settings, storage ----
ui '\|Settings$' ; shot s1-settings 2
ui '^Storage\|' ; shot s2-storage 3
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- Wi-Fi transfer: the host plays the computer ----
ui '\|Settings$' ; sleep 1
ui '^Receive comics over Wi-Fi\|' ; sleep 3
shot w1-wifi 1
addr=$(texts | grep -o 'http://[^ ]*' | head -1)
note "address: $addr"
token=${addr##*k=}
port=$(echo "$addr" | sed -E 's#http://[^:]+:([0-9]+)/.*#\1#')
adb forward tcp:18080 tcp:${port:-8080}
curl -s -o $OUT/wifi-page.html -w "%{http_code}" "http://127.0.0.1:18080/?k=$token" > /tmp/code.txt; note "page: $(cat /tmp/code.txt) $(wc -c < $OUT/wifi-page.html) bytes"
note "wrong code: $(curl -s -o /dev/null -w '%{http_code}' 'http://127.0.0.1:18080/?k=0000')"
note "upload: $(curl -s -X POST --data-binary @comics/PepperCarrot_08.cbz "http://127.0.0.1:18080/upload?k=$token&name=Pepper%26Carrot%20by%20Wifi.cbz")"
note "not a comic: $(curl -s -o /dev/null -w '%{http_code}' -X POST --data-binary @scripts/ui.py "http://127.0.0.1:18080/upload?k=$token&name=ui.py")"
shot w2-received 2
note "files: $(adb shell run-as $APP ls files/Baixadas | tr '\n' ' ')"
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 4
ui '^Library\|' ; shot w3-library 3

# ---- Reading list (.cbl) ----
cat > /tmp/event.cbl <<'X'
<?xml version="1.0"?>
<ReadingList><Name>Pepper Event</Name><Books>
<Book Series="Pepper&amp;Carrot" Number="3" Volume="2014" Year="2014" />
<Book Series="Pepper&amp;Carrot" Number="1" Volume="2014" Year="2014" />
<Book Series="Pepper&amp;Carrot" Number="2" Volume="2014" Year="2014" />
<Book Series="Pepper&amp;Carrot" Number="40" Volume="2014" Year="2022" />
</Books></ReadingList>
X
adb push /tmp/event.cbl /sdcard/Download/event.cbl
ui '^Collections\|' ; sleep 2
shot c0-collections 1
ui '^Import a reading list\|' ; sleep 4
[ -z "$(find_xy '^event.cbl\|')" ] && { ui '^Download\|' ; sleep 3; }
ui '^event.cbl\|' ; sleep 4
shot c1-cbl 1
ui '^Create collection\|' ; sleep 3
shot c2-collection 2
ui '\|Make it yours$' ; sleep 2
shot c3-look 1
# The third swatch (after "automatic" and the first colour).
xy=$(find_xy '^Colour\|')
if [ -n "$xy" ]; then set -- $xy; timeout 20 adb shell input tap $(( $1 + 150 )) $(( $2 + 55 )); fi
shot c4-look-colour 2
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 1
shot c5-collection-tinted 2
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 2
shot c6-collections 2

# ---- Page filters ----
timeout 20 adb shell am force-stop $APP
python3 scripts/look_prefs.py fixColors=true sharp=Strong > /tmp/reader.xml
put /tmp/reader.xml shared_prefs/fwip_reader.xml
timeout 20 adb shell am start -n $APP/.MainActivity -a $APP.OPEN_COMIC --es comic "file:///data/user/0/$APP/files/Baixadas/PepperCarrot_01.cbz"
shot r1-filtered 10
timeout 20 adb shell input tap $(( W / 2 )) $(( H / 2 )); sleep 2
ui '\|Reading settings$' ; sleep 2
for k in 1 2; do timeout 20 adb shell input swipe $(( W / 2 )) $(( H * 3 / 4 )) $(( W / 2 )) $(( H / 3 )) 400; sleep 1; done
shot r2-settings-image 1
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- Split double pages on a phone ----
timeout 20 adb shell am force-stop $APP
adb shell wm size 1080x2400
adb shell wm density 420
python3 scripts/look_prefs.py splitWide=true > /tmp/reader.xml
put /tmp/reader.xml shared_prefs/fwip_reader.xml
timeout 20 adb shell am start -n $APP/.MainActivity -a $APP.OPEN_COMIC --es comic "file:///data/user/0/$APP/files/Baixadas/Repro_Double.cbz"
sleep 12
for k in 1 2 3 4 5 6 7 8; do timeout 20 adb shell input tap 1000 1200; sleep 2.5; done
shot p1-half-a 2
timeout 20 adb shell input tap 1000 1200
shot p2-half-b 3
timeout 20 adb shell input tap 1000 1200
shot p3-after 3
timeout 20 adb shell input tap 540 1200; sleep 2
shot p4-chrome 1

adb shell wm size reset
adb shell wm density reset
timeout 20 adb logcat -d > /tmp/log.txt
grep " Fwip " /tmp/log.txt | tail -60 > $OUT/fwip-log.txt
timeout 30 adb logcat -d -b crash > $OUT/crash.txt
note "crashes: $(head -c 600 $OUT/crash.txt | tr '\n' ' ')"
