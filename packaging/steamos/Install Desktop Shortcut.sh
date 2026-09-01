#!/usr/bin/env bash
set -euo pipefail

bundle_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
launcher=$bundle_root/Launch\ RomRaider2.sh
icon=$bundle_root/RomRaider2/lib/RomRaider2.png
applications=${XDG_DATA_HOME:-$HOME/.local/share}/applications
desktop_file=$applications/romraider2.desktop

mkdir -p "$applications"
sed -e "s|@LAUNCHER@|$launcher|g" -e "s|@ICON@|$icon|g" \
    "$bundle_root/romraider2-steamos.desktop.in" >"$desktop_file"
chmod +x "$desktop_file"

if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$applications" >/dev/null 2>&1 || true
fi

printf 'Installed the RomRaider2 shortcut in Desktop Mode.\n'
