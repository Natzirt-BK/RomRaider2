#!/usr/bin/env bash
set -euo pipefail

release_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$release_root"

[[ -x bin/RomRaider2 ]]
[[ -f lib/runtime/release ]]
grep -q '^JAVA_VERSION="21\.' lib/runtime/release
[[ -f lib/app/RomRaider2.jar ]]
[[ -f lib/app/lib/linux/64/j2534.so ]]
[[ -f config/settings.default.xml ]]
[[ -f config/user/settings.xml ]]
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

grep -Fq 'romraider2.settings.dir=$APPDIR/../../config/user' \
    lib/app/RomRaider2.cfg
grep -Fq 'romraider2.customize.dir=$APPDIR/../../customize' \
    lib/app/RomRaider2.cfg
grep -Fq 'romraider2.log.dir=$APPDIR/../../logs' lib/app/RomRaider2.cfg
grep -Fq 'log4j.configurationFile=$APPDIR/lib/log4j2.xml' \
    lib/app/RomRaider2.cfg

if [[ -f checksums/SHA256SUMS.txt ]]; then
    sha256sum -c checksums/SHA256SUMS.txt
fi

echo "RomRaider2 Linux release verified."
