#!/usr/bin/env bash
set -euo pipefail

release_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$release_root"

[[ -x bin/RomRaider2 ]]
[[ -f lib/runtime/release ]]
grep -q '^JAVA_VERSION="21\.' lib/runtime/release
[[ -f lib/app/RomRaider2.jar ]]
[[ -f lib/app/romraider2-javafx-desktop-1.1.0-rc4.jar ]]
[[ -f lib/app/javafx-base-21.0.10-linux.jar ]]
[[ -f lib/app/javafx-controls-21.0.10-linux.jar ]]
[[ -f lib/app/javafx-graphics-21.0.10-linux.jar ]]
[[ ! -e lib/app/javafx-controls-21.0.10-win.jar ]]
! find lib/app -maxdepth 1 -type f \( -iname '*compose*' -o \
    -iname '*skiko*' -o -iname '*skia*' \) -print -quit | grep -q .
[[ -f lib/app/licenses/JavaFX-21.0.10-GPL-2.0-Classpath-Exception.txt ]]
[[ -f lib/app/licenses/GPL-2.0.txt ]]
[[ -f lib/app/lib/linux/64/j2534.so ]]
[[ -f config/settings.default.xml ]]
[[ -f config/user/settings.xml ]]
[[ -f VERSION.txt ]]
[[ -f docs/RC4_RELEASE_READINESS.md ]]
[[ -f docs/RC4_QUALIFICATION_RECORD.md ]]
[[ -f docs/LINUX_IN_CAR_QUALIFICATION.md ]]
[[ -f customize/j2534Libraries.properties ]]
[[ -f customize/nameSequences.properties ]]
[[ -f customize/ncslearning.properties ]]
[[ -f customize/ssmlearning.properties ]]
[[ -f customize/warningSound.wav ]]
[[ ! -e definitions ]]
! find . -type f \( -iname '*.bin' -o -iname '*.hex' -o -iname '*.srf' \) \
    -print -quit | grep -q .
! grep -Eq 'ecudefinitionfile|<definition |<profile ' \
    config/settings.default.xml
cmp -s config/settings.default.xml config/user/settings.xml
grep -Eq '<display-preferences[^>]+theme="LIGHT"' \
    config/settings.default.xml
grep -Fq 'linux=j2534.so' customize/j2534Libraries.properties
grep -Fxq 'RomRaider2 ECU Studio 1.1.0 Release Candidate 4' VERSION.txt
grep -Eq '^Source commit: [0-9a-f]{40}$' VERSION.txt

grep -Fq 'romraider2.settings.dir=$APPDIR/../../config/user' \
    lib/app/RomRaider2.cfg
grep -Fq 'romraider2.customize.dir=$APPDIR/../../customize' \
    lib/app/RomRaider2.cfg
grep -Fq 'romraider2.log.dir=$APPDIR/../../logs' lib/app/RomRaider2.cfg
grep -Fq 'log4j.configurationFile=$APPDIR/lib/log4j2.xml' \
    lib/app/RomRaider2.cfg
grep -Fq 'romraider2.desktop.shell=javafx' lib/app/RomRaider2.cfg
grep -Fq 'module-path=$APPDIR' lib/app/RomRaider2.cfg
grep -Fq 'add-modules=javafx.controls' lib/app/RomRaider2.cfg

if [[ -f checksums/SHA256SUMS.txt ]]; then
    sha256sum -c checksums/SHA256SUMS.txt
fi

echo "RomRaider2 Linux release verified."
