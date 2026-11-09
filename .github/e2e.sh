#!/bin/bash
set -x
OUT=e2e-out; mkdir -p $OUT
shot() { adb exec-out screencap -p > $OUT/$1.png; }
APP=dev.jitesh.glassisle
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put global hide_error_dialogs 1
adb shell svc power stayon true
adb shell input keyevent 224; adb shell wm dismiss-keyguard
adb shell cmd overlay enable com.android.internal.display.cutout.emulation.hole
adb shell settings put secure enabled_accessibility_services $APP/$APP.GlassIsleAccessibility
adb shell settings put secure accessibility_enabled 1
adb shell cmd notification allow_listener $APP/$APP.GlassIsleListener
sleep 20
adb shell am start -n $APP/.MainActivity; sleep 3; shot 01_main
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell pm grant $APP android.permission.POST_NOTIFICATIONS; adb shell am start -n $APP/.DebugActivity --es mode notif; sleep 9; adb shell cmd notification cancel $APP 7 2>/dev/null; adb shell input keyevent KEYCODE_HOME; sleep 2
adb logcat -c
adb shell am start -n $APP/.DebugActivity --es mode notif
sleep 2; shot 02_notification_peek
W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
adb shell dumpsys notification --noredact | grep -B2 -A12 "Alex" | head -40 > $OUT/notif_dump.txt
adb shell dumpsys notification --noredact > $OUT/notif_full.txt
adb shell input tap 540 110; sleep 2; shot 03_notification_expanded
# helper: tap a view by its text using uiautomator (falls back to given x y)
tap_text() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml $OUT/ui_last.xml >/dev/null 2>&1
  XY=$(python3 - "$1" <<'PY'
import re,sys
x=open('/tmp/x','w') if False else None
import os
t=open(os.environ['OUT']+'/ui_last.xml').read()
m=re.search(r'text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'%re.escape(sys.argv[1]),t)
print(f"{(int(m[1])+int(m[3]))//2} {(int(m[2])+int(m[4]))//2}" if m else "")
PY
); echo "tap_text $1 -> '$XY'" >> $OUT/taps.txt; if [ -n "$XY" ]; then adb shell input tap $XY; else adb shell input tap $2 $3; fi; }
export OUT
adb logcat -c
tap_text Mute 371 321; sleep 2; shot 03b_after_mute_tap
adb logcat -d | grep GI_TEST | grep -i "action=" > $OUT/action_mute.txt
adb shell am broadcast -n $APP/.DebugReceiver -a post >/dev/null; sleep 2; adb shell input tap 540 110; sleep 2
tap_text Open 183 321; sleep 2; shot 03c_after_open_tap
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -2 > $OUT/action_open.txt
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell am broadcast -n $APP/.DebugReceiver -a post >/dev/null; sleep 2; adb shell input tap 540 110; sleep 2
tap_text Reply 564 321; sleep 2; shot 03d_reply_field
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml $OUT/ui_reply.xml >/dev/null 2>&1
adb shell input text Sounds%sgood; sleep 1; shot 03e_reply_typed
python3 - <<'PY' > $OUT/send_xy.txt
import re,os
t=open(os.environ['OUT']+'/ui_reply.xml').read()
m=re.search(r'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',t)
print(f"{int(m[3])+74} {(int(m[2])+int(m[4]))//2}" if m else "")
PY
cat $OUT/send_xy.txt
XY=$(cat $OUT/send_xy.txt); [ -n "$XY" ] && adb shell input tap $XY; sleep 2; shot 03f_after_reply_send
adb logcat -d | grep GI_TEST | grep -i "action=reply" > $OUT/action_reply.txt
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell am broadcast -n $APP/.DebugReceiver -a post >/dev/null; sleep 2; adb shell input tap 540 110; sleep 2
adb shell input swipe $((W/2)) 400 $((W/2)) 40 120; sleep 1.5; shot 04_after_swipe_dismiss
# lock-screen privacy: screen off, post, wake -> island must stay hidden and log skip
adb logcat -c
adb shell input keyevent 26; sleep 2
adb shell am broadcast -n $APP/.DebugReceiver -a post >/dev/null; sleep 2
adb shell input keyevent 224; sleep 3; shot 10_lockscreen_notification
adb shell dumpsys window | grep -E "isKeyguardShowing|mKeyguardShowing|KeyguardController" | head -3 > $OUT/keyguard.txt
adb logcat -d | grep -E " GI +:" > $OUT/lock_log.txt
adb shell input swipe 540 1800 540 600 200; sleep 2
adb shell cmd notification cancel $APP 7 2>/dev/null
adb shell am start -n $APP/.DebugActivity --es mode media; sleep 3; shot 05_music_peek_or_pill
sleep 6; shot 06_music_pill
adb shell input tap 216 80; sleep 2; shot 07_music_expanded
adb shell dumpsys media_session | grep -A12 "glassisle-test" | grep -E "state=PlaybackState|description=" | head -2 > $OUT/media_before.txt
adb shell input tap 730 446; sleep 1.5; shot 07b_music_after_next
adb shell input tap 537 446; sleep 1.5; shot 07c_music_after_pause
adb shell dumpsys media_session | grep -A12 "glassisle-test" | grep -E "state=PlaybackState|description=" | head -2 > $OUT/media_after.txt
sleep 1
adb shell input swipe 540 400 540 40 120; sleep 1
adb shell dumpsys battery unplug; sleep 2; adb shell dumpsys battery set level 80; adb shell dumpsys battery set ac 1; sleep 2; shot 09_charging
sleep 8
adb shell am start -n $APP/.DebugActivity --es mode timer; sleep 3; shot 08_timer
adb shell dumpsys battery reset
adb shell dumpsys display | grep -iE "cutout|DisplayCutout" | head -5 > $OUT/cutout.txt
adb shell dumpsys window windows | grep -i glassisle | head -5 > $OUT/overlay_windows.txt
adb logcat -d > $OUT/logcat_full.txt
grep -E "FATAL|AndroidRuntime|GI_TEST| GI +:|dev.jitesh.glassisle.*(Exception|Error)" $OUT/logcat_full.txt | head -60 > $OUT/logcat_filtered.txt
exit 0
