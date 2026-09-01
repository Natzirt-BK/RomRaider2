#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
application_image=${ROMRAIDER2_APP_IMAGE:-$repo_root/build/java21/RomRaider2}
output_root=${1:-$repo_root/build/steamos}
bundle_name=RomRaider2_SteamOS_1.1.0_RC4_x64

[[ -x "$application_image/bin/RomRaider2" ]] || {
    echo "Build the Linux Java 21 application image first: $application_image" >&2
    exit 1
}
mkdir -p "$output_root"
output_root=$(cd -- "$output_root" && pwd)
destination=$output_root/$bundle_name
[[ ! -e "$destination" ]] || {
    echo "Destination already exists; preserving it: $destination" >&2
    exit 2
}
stage=$(mktemp -d "$output_root/.romraider2-steamos.XXXXXX")
trap 'rm -rf -- "$stage"' EXIT HUP INT TERM
bundle=$stage/$bundle_name
mkdir -p "$bundle"
cp -a -- "$application_image" "$bundle/RomRaider2"
cp -- "$repo_root/packaging/steamos/Launch RomRaider2.sh" "$bundle/"
cp -- "$repo_root/packaging/steamos/Install Desktop Shortcut.sh" "$bundle/"
cp -- "$repo_root/packaging/steamos/romraider2-steamos.desktop.in" "$bundle/"
chmod +x "$bundle/Launch RomRaider2.sh" \
    "$bundle/Install Desktop Shortcut.sh"

[[ -f "$bundle/RomRaider2/lib/runtime/release" ]]
grep -q '^JAVA_VERSION="21\.' "$bundle/RomRaider2/lib/runtime/release"
[[ -f "$bundle/RomRaider2/lib/app/romraider2-compose-logger-1.1.0-rc4.jar" ]]
[[ -f "$bundle/RomRaider2/lib/app/org-jetbrains-skiko-skiko-awt-runtime-linux-x64-0.150.1.jar" ]]
[[ ! -e "$bundle/RomRaider2/lib/app/org-jetbrains-skiko-skiko-awt-runtime-windows-x64-0.150.1.jar" ]]
[[ ! -e "$bundle/RomRaider2/lib/app/org-jetbrains-skiko-skiko-awt-runtime-macos-arm64-0.150.1.jar" ]]
[[ ! -e "$bundle/RomRaider2/lib/app/org-jetbrains-skiko-skiko-awt-runtime-macos-x64-0.150.1.jar" ]]
grep -Fq 'GenericName=ECU Tools' \
    "$bundle/romraider2-steamos.desktop.in"
if command -v desktop-file-validate >/dev/null 2>&1; then
    validation_file=$stage/romraider2-steamos.desktop
    sed -e "s|@LAUNCHER@|$bundle/Launch RomRaider2.sh|g" \
        -e "s|@ICON@|$bundle/RomRaider2/lib/RomRaider2.png|g" \
        "$bundle/romraider2-steamos.desktop.in" >"$validation_file"
    desktop-file-validate "$validation_file"
    rm -- "$validation_file"
fi
if find "$bundle" -type f \( \
        -iname '*.bin' -o -iname '*.hex' -o -iname '*.srf' \) \
        -print -quit | grep -q .; then
    echo "Vehicle ROM content must not be included in the SteamOS bundle." >&2
    exit 5
fi

mv -- "$bundle" "$destination"
rmdir -- "$stage"
trap - EXIT HUP INT TERM
printf 'SteamOS Desktop Mode bundle: %s\n' "$destination"
