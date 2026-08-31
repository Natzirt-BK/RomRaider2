#!/usr/bin/env bash
set -eu

application_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
bundled_java=$application_root/runtime/bin/java
if [[ -x "$bundled_java" ]]; then
    java_cmd=$bundled_java
else
    java_cmd=$(command -v java || true)
fi

[[ -n "$java_cmd" ]] || {
    echo "No bundled runtime was found and Java is not available on PATH." >&2
    exit 1
}

native_dir=$application_root/lib/linux/64
[[ -d "$native_dir" ]] || {
    echo "Required 64-bit native library directory is missing: $native_dir" >&2
    exit 1
}

cd "$application_root"
exec "$java_cmd" \
    -Djava.library.path="$native_dir" \
    -Dromraider2.j2534.library="$native_dir/j2534.so" \
    -Dawt.useSystemAAFontSettings=lcd \
    -Dswing.aatext=true \
    -Dsun.java2d.d3d=false \
    -Xms64M -Xmx768M \
    -jar RomRaider2.jar "$@"
