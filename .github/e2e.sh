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
adb shell am start -n $APP/.DebugActivity --es mode timer; sleep 8; adb shell am force-stop com.android.shell 2>/dev/null; adb shell input keyevent KEYCODE_HOME; sleep 4
adb logcat -c
adb shell cmd notification post -S bigtext -t "Alex" t1 "Hey, are you free tonight? Let's grab dinner at 8."
sleep 2; shot 02_notification_peek
W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
adb shell input tap 216 80; adb shell input tap $((W/2)) 80; sleep 1.5; shot 03_notification_expanded
adb shell input swipe $((W/2)) 400 $((W/2)) 40 120; sleep 1.5; shot 04_after_swipe_dismiss
adb shell am start -n $APP/.DebugActivity --es mode media; sleep 3; shot 05_music_peek_or_pill
sleep 6; shot 06_music_pill
adb shell input tap 216 80; adb shell input tap $((W/2)) 80; sleep 1.5; shot 07_music_expanded
adb shell dumpsys media_session | grep -A12 "glassisle-test" | grep -E "state=PlaybackState|description=" | head -2 > $OUT/media_before.txt
adb shell input tap 540 520; sleep 1.5; shot 07b_music_after_tap_play
adb shell dumpsys media_session | grep -A12 "glassisle-test" | grep -E "state=PlaybackState|description=" | head -2 > $OUT/media_after.txt
adb shell input tap 216 80; sleep 1
adb shell input swipe $((W/2)) 400 $((W/2)) 40 120; adb shell am start -n $APP/.DebugActivity --es mode timer; sleep 3; shot 08_timer
adb shell dumpsys battery set ac 1; adb shell dumpsys battery set level 80; sleep 1; adb shell dumpsys battery unplug; sleep 1; adb shell dumpsys battery set ac 1; sleep 2; shot 09_charging
adb shell dumpsys battery reset
adb shell dumpsys window windows | grep -i glassisle | head -5 > $OUT/overlay_windows.txt
adb logcat -d > $OUT/logcat_full.txt
grep -E "FATAL|AndroidRuntime|GI_TEST| GI +:|dev.jitesh.glassisle.*(Exception|Error)" $OUT/logcat_full.txt | head -60 > $OUT/logcat_filtered.txt
exit 0
