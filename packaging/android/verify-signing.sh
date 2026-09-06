#!/usr/bin/env bash
set -euo pipefail
[[ $# -ge 2 ]] || { echo 'Usage: verify-signing.sh expected-certificate-sha256 APK...' >&2; exit 2; }
expected=$1
shift
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo 'Expected signing fingerprint is invalid.' >&2; exit 2; }
apk_signer=${APKSIGNER:-apksigner}
for apk in "$@"; do
    result=$("$apk_signer" verify --verbose --print-certs "$apk")
    fingerprint=$(printf '%s\n' "$result" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
    [[ "$fingerprint" == "$expected" ]] && \
        [[ "$result" == *'Number of signers: 1'* ]] || {
        echo "Unexpected signing identity: $apk" >&2; exit 1;
    }
    printf 'Verified distribution signing: %s\n' "$apk"
done
