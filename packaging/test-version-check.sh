#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
current=$(awk -F= '$1 == "version.buildnumber" {print $2}' "$repo_root/version.properties")
fixture=$(mktemp -d)
trap 'rm -rf -- "$fixture"' EXIT
paths=(version.properties release_notes.txt packaging/versioned-paths.txt
    platform/shared-core/build.gradle.kts ui/javafx-desktop/build.gradle.kts
    ui/compose-logger/build.gradle.kts platform/android/app/build.gradle.kts)
while IFS= read -r path; do [[ -z "$path" ]] || paths+=("$path"); done < "$repo_root/packaging/versioned-paths.txt"
for path in "${paths[@]}"; do
    mkdir -p "$fixture/$(dirname "$path")"
    cp -- "$repo_root/$path" "$fixture/$path"
done
bash "$repo_root/packaging/verify-version.sh" "$fixture"
reject() {
    if bash "$repo_root/packaging/verify-version.sh" "$fixture" >/dev/null 2>&1; then
        echo "Version guard accepted $1" >&2; exit 1
    fi
}
sed -i 's/^version.android.code=.*/version.android.code=110412/' "$fixture/version.properties"
reject 'an unchanged public Android versionCode'
cp -- "$repo_root/version.properties" "$fixture/version.properties"
sed -i 's/^version.buildnumber=.*/version.buildnumber=0.0.0-preview/' "$fixture/version.properties"
reject 'a stage-suffixed version'
cp -- "$repo_root/version.properties" "$fixture/version.properties"
sed -i "s/${current//./\\.}/0.0.0/g" "$fixture/packaging/java21/VERIFY_RELEASE_LINUX.sh"
reject 'a stale package verifier'
cp -- "$repo_root/packaging/java21/VERIFY_RELEASE_LINUX.sh" "$fixture/packaging/java21/VERIFY_RELEASE_LINUX.sh"
sed -i "s/${current//./\\.}/0.0.0/g" "$fixture/.github/workflows/platform-previews.yaml"
reject 'stale workflow artifact names'
echo 'Version guard fixtures passed.'
