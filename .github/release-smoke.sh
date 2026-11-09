#!/bin/bash
set -x
OUT=smoke-out; mkdir -p $OUT
shot() { adb exec-out screencap -p > $OUT/$1.png; }
APP=dev.jitesh.glassisle
A=$(find dl -name app-release.apk | head -1); echo "$A" > $OUT/apk_path.txt
sha256sum "$A" > $OUT/sha256.txt
adb install -r "$A" > $OUT/install.txt 2>&1; cat $OUT/install.txt
adb shell dumpsys package $APP | grep -E "versionName|versionCode|minSdk|targetSdk|installerPackageName|flags=|requested permissions" -A0 > $OUT/pkg.txt
adb shell dumpsys package $APP | grep -A8 "requested permissions" >> $OUT/pkg.txt
adb shell settings put global hide_error_dialogs 1
adb shell svc power stayon true
adb shell input keyevent 224; adb shell wm dismiss-keyguard
adb shell cmd overlay enable com.android.internal.display.cutout.emulation.hole
adb shell settings put secure enabled_accessibility_services $APP/$APP.GlassIsleAccessibility
adb shell settings put secure accessibility_enabled 1
adb shell cmd notification allow_listener $APP/$APP.GlassIsleListener
sleep 20
adb shell am start -n $APP/.MainActivity; sleep 3; shot 01_release_main
adb shell dumpsys accessibility | grep -iE "glassisle" | head -5 > $OUT/a11y.txt
adb shell dumpsys notification | grep -iE "listeners|glassisle" | head -8 > $OUT/listener.txt
adb shell pidof $APP > $OUT/pid.txt
adb shell input keyevent KEYCODE_HOME; sleep 2
# charging card with nothing else open
adb shell dumpsys battery unplug; sleep 2; adb shell dumpsys battery set level 80; adb shell dumpsys battery set ac 1; sleep 2; shot 02_release_charging_peek
sleep 3; shot 02b_release_charging
sleep 8; shot 02c_release_charging_after
adb shell dumpsys battery reset
# real system-posted notification through the release build's listener
adb shell "cmd notification post -S bigtext -t Alex smoke 'Hey are you free tonight, lets grab dinner at 8'" > $OUT/post.txt 2>&1
sleep 1; shot 03_release_notification; sleep 1; shot 03b_release_notification
adb shell dumpsys notification --noredact | grep -c "Alex" > $OUT/notif_count.txt
adb shell dumpsys window windows | grep -i glassisle | head -5 > $OUT/overlay_windows.txt
adb logcat -d > $OUT/logcat_full.txt
grep -E "FATAL|AndroidRuntime|dev.jitesh.glassisle.*(Exception|Error)" $OUT/logcat_full.txt | head -30 > $OUT/crashes.txt
exit 0
