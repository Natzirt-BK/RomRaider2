#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
jdk_root=${JAVA_HOME:?Set JAVA_HOME to a Java 21 JDK}
application_jar=${1:-$repo_root/build/linux/lib/RomRaider2.jar}

[[ -x "$jdk_root/bin/jdeps" ]] || {
    echo "JAVA_HOME does not contain jdeps: $jdk_root" >&2
    exit 1
}
[[ -f "$application_jar" ]] || {
    echo "Build RomRaider2 first; jar not found: $application_jar" >&2
    exit 1
}

"$jdk_root/bin/jdeps" \
    --multi-release 21 \
    --ignore-missing-deps \
    --print-module-deps \
    --class-path "$repo_root/lib/common/*:$repo_root/lib/linux/*" \
    "$application_jar"
