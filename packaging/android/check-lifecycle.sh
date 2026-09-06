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
run_phase() {
    local phase=$1 result
    "$adb_command" -s "$serial" shell am force-stop com.romraider.mobile.automation
    result=$("$adb_command" -s "$serial" shell am instrument -w -e phase "$phase" \
        com.romraider.mobile.automation.test/com.romraider.mobile.LoggerSetupInstrumentation)
    printf '%s\n' "$result"
    [[ "$result" == *"PASS logger setup automation: $phase"* && "$result" != *'FAIL logger setup automation'* ]]
}
"$adb_command" -s "$serial" install -r "$initial"
"$adb_command" -s "$serial" install -r "$test_apk"
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
echo 'PASS: setup restoration, source removal, same-key upgrade, retained log export, gauge view/session/CSV continuity, clear selection, corrupt setup, and no automatic logging.'
