#!/bin/bash
# Opens test comics on the tablet emulator and turns pages by dragging and by tapping,
# taking screenshots during and after each turn.
set -x
APP=io.github.christiandonatti.fwip
OUT=repro
mkdir -p $OUT
cap() { timeout 30 adb shell screencap -p /sdcard/s.png; timeout 30 adb pull /sdcard/s.png "$OUT/$1.png" > /dev/null; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell "run-as $APP mkdir -p files/Baixadas"
for f in comics/*.cb?; do
  b=$(basename "$f" | tr ' ' '_')
  adb exec-in "run-as $APP sh -c 'cat > files/Baixadas/$b'" < "$f"
done
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell svc power stayon true
adb shell wm size 1920x1200
adb shell wm density 240
adb shell am start -n $APP/.MainActivity
sleep 20
adb logcat -c

for name in Repro_Double.cbz Repro_Double.cbr Repro_Solid.cbr; do
  tag=${name%.*}_${name##*.}
  adb shell am force-stop $APP
  adb shell am start -n $APP/.MainActivity -a $APP.OPEN_COMIC --es comic "file:///data/user/0/$APP/files/Baixadas/$name"
  sleep 8
  cap ${tag}_00_open
  # Drag turns: hold the page half way to see both sides of the curl, then let go.
  for k in 1 2 3 4 5 6; do
    timeout 20 adb shell input motionevent DOWN 1840 1100
    for s in 1 2 3; do timeout 20 adb shell input motionevent MOVE $(( 1840 - 260 * s )) $(( 1100 - 60 * s )); done
    cap ${tag}_d${k}_a_mid
    for s in 4 5 6; do timeout 20 adb shell input motionevent MOVE $(( 1840 - 260 * s )) $(( 1100 - 60 * s )); done
    timeout 20 adb shell input motionevent UP 200 700
    sleep 2.5
    cap ${tag}_d${k}_b_after
  done
  # Tap turns on the right side.
  for k in 1 2 3 4 5; do
    adb shell input tap 1800 600
    sleep 0.25
    cap ${tag}_t${k}_a_mid
    sleep 2.5
    cap ${tag}_t${k}_b_after
  done
done
adb logcat -d | grep -iE "fwip|AndroidRuntime|FATAL" | tail -80 > $OUT/logcat.txt
ls $OUT | head -100
