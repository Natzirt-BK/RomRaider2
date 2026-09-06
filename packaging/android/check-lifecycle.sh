#!/usr/bin/env bash
set -euo pipefail
[[ $# == 4 && "$1" == emulator-* ]] || {
    echo 'Usage: check-lifecycle.sh emulator-SERIAL initial-automation.apk test.apk upgraded-automation.apk' >&2; exit 2;
}
serial=$1
initial=$2
test_apk=$3
upgrade=$4
adb_command=${ADB:-adb}
aapt_command=${AAPT:-aapt}
for apk in "$initial" "$upgrade"; do
    metadata=$("$aapt_command" dump badging "$apk")
    [[ "$metadata" == "package: name='com.romraider.mobile.automation' "* ]] || {
        echo 'Refusing to install a non-automation APK.' >&2; exit 2;
    }
done
metadata=$("$aapt_command" dump badging "$test_apk")
[[ "$metadata" == "package: name='com.romraider.mobile.automation.test' "* ]] || {
    echo 'Unexpected instrumentation APK.' >&2; exit 2;
}
[[ $("$adb_command" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]] || {
    echo 'Refusing lifecycle tests on a physical device.' >&2; exit 2;
}
test_sdk=$("$adb_command" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')
[[ "$test_sdk" =~ ^[0-9]+$ && "$test_sdk" -ge 33 ]] || {
    echo 'Service/notification lifecycle automation requires an API 33+ emulator.' >&2; exit 2;
}
run_phase() {
    local phase=$1 result
    if [[ "$phase" != background-after-death ]]; then
        "$adb_command" -s "$serial" shell am force-stop com.romraider.mobile.automation
    fi
    result=$("$adb_command" -s "$serial" shell am instrument -w -e phase "$phase" \
        com.romraider.mobile.automation.test/com.romraider.mobile.LoggerSetupInstrumentation)
    printf '%s\n' "$result"
    [[ "$result" == *"PASS logger setup automation: $phase"* && "$result" != *'FAIL logger setup automation'* ]]
}
"$adb_command" -s "$serial" install -r "$initial"
"$adb_command" -s "$serial" install -r "$test_apk"
"$adb_command" -s "$serial" shell pm grant com.romraider.mobile.automation android.permission.POST_NOTIFICATIONS
run_phase seed
run_phase verify
"$adb_command" -s "$serial" install -r "$upgrade"
run_phase verify
run_phase gauges
run_phase live-gauges
run_phase clear
run_phase verify-empty
run_phase corrupt
run_phase verify-corrupt
run_phase calculated-gauges
run_phase channel-transfer
run_phase background-service
"$adb_command" -s "$serial" shell pm revoke com.romraider.mobile.automation android.permission.POST_NOTIFICATIONS
run_phase background-denied
"$adb_command" -s "$serial" shell pm grant com.romraider.mobile.automation android.permission.POST_NOTIFICATIONS
"$adb_command" -s "$serial" shell am force-stop com.romraider.mobile.automation
death_result=$("$adb_command" -s "$serial" shell am instrument -w -e phase background-process-death \
    com.romraider.mobile.automation.test/com.romraider.mobile.LoggerSetupInstrumentation)
printf '%s\n' "$death_result"
[[ "$death_result" == *'READY for synthetic recording process-death check'* && "$death_result" == *'Process crashed'* ]]
# Inspect the actual post-kill service state before instrumentation can restart
# the target process. Do not hide an unwanted restart with another force-stop.
sleep 1
service_after_death=$("$adb_command" -s "$serial" shell dumpsys activity services com.romraider.mobile.automation)
[[ "$service_after_death" != *'isForeground=true'* ]]
run_phase background-after-death
echo 'PASS: setup restoration, source removal, same-key upgrade, retained log export, gauge view/session/CSV continuity, calculated channels, reviewed channel transfer, clear selection, corrupt setup, and no automatic logging.'
echo 'PASS: service-owned synthetic capture through Home/screen-off and recreation, notification/in-app Stop, stale requests, denied notifications, and retained spool prefix with no restart after process death.'
