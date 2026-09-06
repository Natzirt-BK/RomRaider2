#!/usr/bin/env bash
set -euo pipefail
[[ $# -gt 0 ]] || { echo 'Usage: verify-notices.sh APK [APK ...]' >&2; exit 2; }
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
for apk in "$@"; do
    cmp "$repo_root/license.txt" <(unzip -p "$apk" assets/notices/license.txt)
    cmp "$repo_root/licenses/STI-wordmark-NOTICE.txt" <(unzip -p "$apk" assets/notices/STI-wordmark-NOTICE.txt)
    echo "Verified bundled notices: $apk"
done
