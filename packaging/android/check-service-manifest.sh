#!/usr/bin/env bash
set -euo pipefail
[[ $# == 2 && ( "$1" == production || "$1" == automation ) ]] || {
    echo 'Usage: check-service-manifest.sh production|automation app.apk' >&2; exit 2;
}
kind=$1
apk=$2
aapt_command=${AAPT:-aapt}
trap 'echo "Recording-service manifest isolation failed for $kind: $apk" >&2' ERR
metadata=$("$aapt_command" dump badging "$apk")
if [[ "$kind" == automation ]]; then
    [[ "$metadata" == "package: name='com.romraider.mobile.automation' "* ]]
    expected_type=0x1
else
    [[ "$metadata" == "package: name='com.romraider.mobile.preview' "* ||
       "$metadata" == "package: name='com.romraider.mobile.preview.openporttest' "* ||
       "$metadata" == "package: name='com.romraider.mobile' "* ]]
    expected_type=0x10
fi
manifest=$("$aapt_command" dump xmltree "$apk" AndroidManifest.xml)
service=$(awk '/android:name.*="com.romraider.mobile.ReadOnlyLoggingService"/ {found=1; next}
    found && /E:/ {exit} found {print}' <<< "$manifest")
attribute() { awk -v attribute="$1" '$0 ~ "android:" attribute "\\(" {print $NF}' <<< "$service"; }
[[ $(attribute exported) == '0x12)0x0' ]]
[[ $(attribute stopWithTask) == '0x12)0x0' ]]
[[ $(attribute foregroundServiceType) == "0x11)$expected_type" ]]
[[ "$manifest" == *'"android.permission.FOREGROUND_SERVICE"'* ]]
[[ "$manifest" == *'"android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"'* ]]
if [[ "$kind" == production ]]; then
    [[ "$manifest" != *'"android.permission.FOREGROUND_SERVICE_DATA_SYNC"'* ]]
else
    [[ "$manifest" == *'"android.permission.FOREGROUND_SERVICE_DATA_SYNC"'* ]]
fi
echo "PASS: $kind recording service is non-exported and uses the expected manifest/type permissions."
