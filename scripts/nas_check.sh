#!/bin/bash
# Tablet to NAS on an emulator: comics in a device folder go to a Samba share on the host
# (10.0.2.2), a comic in both places shows once, and one deleted on the device keeps showing
# from the NAS. Screenshots and notes go to repro/.
set -x
APP=io.github.christiandonatti.fwip
OUT=repro
NAS=/tmp/nas
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
nas() { (cd $NAS && find . -type f | sort | sed 's/^/  /'); }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell wm size ${W}x${H}
adb shell wm density 240
adb shell pm clear $APP

# Comics on the device: two in a subfolder, one loose. #3 is on the NAS already, same file.
adb shell mkdir -p /sdcard/Download/Fwip/Pepper
adb push comics/PepperCarrot_01.cbz /sdcard/Download/Fwip/Pepper/
adb push comics/PepperCarrot_02.cbz /sdcard/Download/Fwip/Pepper/
adb push comics/PepperCarrot_03.cbz /sdcard/Download/Fwip/
note "NAS before: $(nas | tr '\n' ' ')"

# The NAS server, its HQs folder in the library, and uploads there (any network: the emulator is not on Wi-Fi).
timeout 20 adb exec-in "run-as $APP sh -c 'mkdir -p files shared_prefs; cat > files/servers.json'" <<'J'
[{"id":"nas","name":"NAS","kind":"Smb","url":"smb://10.0.2.2/Comics","user":"fwip","password":"fwippass"}]
J
printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" '<map>' '    <set name="folders">' '        <string>fwip-net://nas/HQs</string>' '    </set>' '</map>' > /tmp/fwip_prefs.xml
cat /tmp/fwip_prefs.xml
timeout 20 adb exec-in "run-as $APP sh -c 'cat > shared_prefs/fwip.xml'" < /tmp/fwip_prefs.xml
python3 scripts/look_prefs.py welcomed=true > /tmp/fwip_ui.xml
timeout 20 adb exec-in "run-as $APP sh -c 'cat > shared_prefs/fwip_ui.xml'" < /tmp/fwip_ui.xml
python3 scripts/look_prefs.py target=fwip-net://nas/HQs wifi=false > /tmp/fwip_upload.xml
timeout 20 adb exec-in "run-as $APP sh -c 'cat > shared_prefs/fwip_upload.xml'" < /tmp/fwip_upload.xml
timeout 20 adb shell "run-as $APP sh -c 'ls -la shared_prefs files; cat shared_prefs/fwip.xml'"
restart 15
timeout 20 adb shell "run-as $APP sh -c 'ls -la shared_prefs; cat shared_prefs/fwip.xml'"
shot n1-nas-only 2

# Add the device folder through the system picker.
ui '\|Settings$' ; sleep 2
ui '^Add folder\|' ; sleep 4
shot n2-picker 1
# The picker opens at the storage root: Download, then Fwip.
if [ -z "$(find_xy '^Fwip\|')" ]; then
  ui '^Download\|' ; sleep 3
fi
ui '^Fwip\|' ; sleep 3
shot n3-picker-fwip 1
ui '(?i)^use this folder' ; sleep 3
ui '(?i)^allow\|' ; sleep 3
shot n3b-after-allow 1
# Close the settings sheet if it is still open (Back on the home screen would leave the app).
[ -n "$(find_xy '^Add folder\|')" ] && timeout 20 adb shell input keyevent KEYCODE_BACK
note "folders: $(timeout 20 adb shell "run-as $APP sh -c 'cat shared_prefs/fwip.xml'" | tr -d '\n' | cut -c1-400)"

# Scan, then the upload runs in the background.
for i in $(seq 1 24); do
  sleep 5
  [ -f "$NAS/HQs/Pepper/PepperCarrot_02.cbz" ] && [ -z "$(find $NAS -name '*.fwip-part')" ] && break
done
sleep 5
note "NAS after: $(nas | tr '\n' ' ')"
ls -laR $NAS > $OUT/nas.txt
for f in Pepper/PepperCarrot_01.cbz Pepper/PepperCarrot_02.cbz; do
  if cmp -s "comics/$(basename $f)" "$NAS/HQs/$f"; then note "same bytes: $f"; else note "DIFFERENT OR MISSING: $f"; fi
done
shot n4-home 2
ui '^Library\|' ; sleep 3
shot n5-library 2
ui '\|Settings$' ; sleep 2
ui '^Sync and backup\|' ; sleep 3
timeout 20 adb shell input swipe $(( W / 2 )) $(( H * 3 / 4 )) $(( W / 2 )) $(( H / 4 )) 500; sleep 1
shot n6-sync-upload 2
timeout 20 adb shell input keyevent KEYCODE_BACK; sleep 1

# Deleted on the device: it stays in the library, read from the NAS.
adb shell rm /sdcard/Download/Fwip/Pepper/PepperCarrot_01.cbz
restart 20
ui '^Library\|' ; sleep 3
shot n7-after-delete 2
sleep 20
note "NAS at the end: $(nas | tr '\n' ' ')"
timeout 20 adb logcat -d > /tmp/log.txt
grep " Fwip \|NasUpload" /tmp/log.txt > $OUT/fwip-log.txt
note "sent lines: $(grep -c 'upload: sent' /tmp/log.txt)"
timeout 30 adb logcat -d -b crash > $OUT/crash.txt
note "crashes: $(head -c 600 $OUT/crash.txt | tr '\n' ' ')"
adb shell wm size reset
adb shell wm density reset
