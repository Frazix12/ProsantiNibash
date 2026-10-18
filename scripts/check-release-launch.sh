#!/bin/sh
# Run against the installed release APK; never clears application data.
set -eu
package=com.irfan.prosantinibash
adb shell am force-stop "$package"
adb shell am start -W -n "$package/.MainActivity"
sleep 3
adb shell pidof "$package" >/dev/null || {
    adb logcat -d -b crash -t 50
    exit 1
}
adb shell dumpsys activity activities | rg "(ResumedActivity|topResumedActivity).*$package/"
