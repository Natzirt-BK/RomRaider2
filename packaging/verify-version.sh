#!/usr/bin/env bash
set -euo pipefail

repo_root=${1:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)}
fail() { echo "Version check failed: $*" >&2; exit 1; }
property() { awk -F= -v key="$1" '$1 == key {sub(/\r$/, "", $2); print $2}' "$repo_root/version.properties"; }
version=$(property version.buildnumber)
[[ "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || fail 'Use numeric major.minor.patch without stage suffixes.'
[[ "$version" = "$(property version.major).$(property version.minor).$(property version.patch)" ]] || fail 'Desktop version components disagree.'
android_code=$(property version.android.code)
[[ "$android_code" =~ ^[1-9][0-9]{0,8}$ ]] || fail 'Invalid Android versionCode.'
(( android_code > 110412 )) || fail 'Android versionCode must exceed the 1.1.8 baseline (110412).'
IFS=. read -r major minor patch <<< "$version"
(( major > 1 || (major == 1 && minor > 1) || (major == 1 && minor == 1 && patch > 8) )) || fail 'Version must exceed the 1.1.8 baseline.'
[[ "$(head -n 1 "$repo_root/release_notes.txt")" = "RomRaider2 ECU Studio $version" ]] || fail 'Release-notes heading disagrees.'

for path in platform/shared-core/build.gradle.kts ui/javafx-desktop/build.gradle.kts \
        ui/compose-logger/build.gradle.kts platform/android/app/build.gradle.kts; do
    grep -Fq 'version.buildnumber' "$repo_root/$path" || fail "$path must consume version.properties."
    if grep -Eq 'version(Name)? = "[0-9]+\.[0-9]+\.[0-9]+"' "$repo_root/$path"; then
        fail "$path hardcodes a separate application version."
    fi
done
grep -Fq 'version.android.code' "$repo_root/platform/android/app/build.gradle.kts" || fail 'Android must consume the shared versionCode.'

# Package scripts still contain explicit artifact names. Fail the build if any
# of those or their workflow names drift from the canonical application version.
prefix='(RomRaider2([ _]ECU[ _]Studio|_SteamOS)?[-_ ]|romraider2-(javafx-desktop|compose-logger)-|--app-version[ ]+|release-)'
while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    [[ -f "$repo_root/$path" ]] || fail "Missing versioned path: $path"
    found=$(sed 's/\\\././g' "$repo_root/$path" | grep -Eo "${prefix}[0-9]+\.[0-9]+\.[0-9]+" | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+' || true)
    [[ -n "$found" ]] || fail "No application version found in $path."
    while IFS= read -r candidate; do
        [[ "$candidate" = "$version" ]] || fail "$path labels $candidate instead of $version."
    done <<< "$found"
done < "$repo_root/packaging/versioned-paths.txt"
echo "Application versions agree: $version / Android $android_code."
