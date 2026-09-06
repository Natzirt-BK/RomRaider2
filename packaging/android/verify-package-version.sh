#!/usr/bin/env bash
set -euo pipefail
[[ $# = 2 ]] || { echo 'Usage: verify-package-version.sh expected-package-id APK' >&2; exit 2; }
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
expected_package=$1
case "$expected_package" in
    com.romraider.mobile.preview|com.romraider.mobile.preview.openporttest) ;;
    *) echo 'Not a recognized distribution application ID.' >&2; exit 2 ;;
esac
version=$(awk -F= '$1 == "version.buildnumber" {print $2}' "$repo_root/version.properties")
code=$(awk -F= '$1 == "version.android.code" {print $2}' "$repo_root/version.properties")
badging=$("${AAPT:-aapt}" dump badging "$2")
package_line=$(grep '^package: ' <<< "$badging")
package_id=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$package_line")
package_version=$(sed -n "s/.* versionName='\([^']*\)'.*/\1/p" <<< "$package_line")
package_code=$(sed -n "s/.* versionCode='\([^']*\)'.*/\1/p" <<< "$package_line")
[[ "$package_id" = "$expected_package" && "$package_version" = "$version" && "$package_code" = "$code" ]] || {
    echo "APK identity/version mismatch: $package_id $package_version ($package_code)." >&2; exit 1;
}
echo "Verified APK version: $package_id $version ($code)."
