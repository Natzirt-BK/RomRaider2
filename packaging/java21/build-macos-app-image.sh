#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
[[ "$(uname -s)" = Darwin ]] || {
    echo "The macOS application image must be built on macOS." >&2
    exit 2
}

case "$(uname -m)" in
    arm64|aarch64)
        package_arch=arm64
        other_macos_renderer=org-jetbrains-skiko-skiko-awt-runtime-macos-x64-0.150.1.jar
        ;;
    x86_64|amd64)
        package_arch=x64
        other_macos_renderer=org-jetbrains-skiko-skiko-awt-runtime-macos-arm64-0.150.1.jar
        ;;
    *) echo "Unsupported macOS architecture: $(uname -m)" >&2; exit 2 ;;
esac

jdk_root=${JAVA_HOME:?Set JAVA_HOME to a Java 21 JDK}
application_jar=${ROMRAIDER2_JAR:-$repo_root/build/macos/lib/RomRaider2.jar}
compose_root=$repo_root/build/compose/macos-$package_arch
output_root=${1:-$repo_root/build/java21-macos-$package_arch}
destination=$output_root/RomRaider2.app
renderer=org-jetbrains-skiko-skiko-awt-runtime-macos-$package_arch-0.150.1.jar
modules=java.base,java.compiler,java.desktop,java.management,java.naming,java.rmi,java.scripting,java.sql,jdk.unsupported

for tool in java javap jdeps jpackage; do
    [[ -x "$jdk_root/bin/$tool" ]] || {
        echo "JAVA_HOME does not contain $tool: $jdk_root" >&2
        exit 1
    }
done
[[ "$($jdk_root/bin/java -XshowSettings:properties -version 2>&1 | \
        sed -n 's/^[[:space:]]*java.specification.version = //p')" = 21 ]] || {
    echo "A Java 21 JDK is required: $jdk_root" >&2
    exit 1
}
[[ -f "$application_jar" ]] || {
    echo "Build the macOS application jar first: $application_jar" >&2
    exit 1
}
[[ -f "$compose_root/romraider2-compose-logger-1.1.0-rc4.jar" && \
   -f "$compose_root/$renderer" ]] || {
    echo "Stage the Compose workspace on this Mac before packaging." >&2
    exit 1
}
[[ ! -e "$destination" ]] || {
    echo "Destination already exists; preserving it: $destination" >&2
    exit 2
}

class_version=$(
    "$jdk_root/bin/javap" -verbose -classpath "$application_jar" \
        com.romraider.ECUExec | sed -n 's/^[[:space:]]*major version: //p'
)
[[ "$class_version" = 65 ]] || {
    echo "RomRaider2.jar is not Java 21 bytecode: $class_version" >&2
    exit 4
}
"$repo_root/packaging/java21/verify-audited-dependencies.sh"

mkdir -p "$output_root"
stage=$(mktemp -d "${TMPDIR:-/tmp}/romraider2-macos.XXXXXX")
trap 'rm -rf -- "$stage"' EXIT HUP INT TERM
input=$stage/input
mkdir -p "$input/lib/common" "$input/lib" "$input/plugins" \
    "$input/licenses" "$input/defaults" "$input/customize"
cp -- "$application_jar" "$input/RomRaider2.jar"
cp -- "$compose_root"/*.jar "$input/"
for dependency in "$repo_root"/lib/common/*.jar; do
    case "${dependency##*/}" in
        Graph3d.jar|j3dcore.jar|j3dutils.jar|vecmath.jar) continue ;;
    esac
    cp -- "$dependency" "$input/lib/common/"
done
cp -- "$repo_root/lib/log4j2.xml" "$input/lib/log4j2.xml"
cp -- "$repo_root"/plugins/*.plugin "$input/plugins/"
cp -- "$repo_root"/licenses/* "$input/licenses/"
cp -- "$repo_root/packaging/default/settings.xml" \
    "$input/defaults/settings.xml"
cp -- "$repo_root"/customize/* "$input/customize/"

detected_modules=$(JAVA_HOME="$jdk_root" \
        "$repo_root/packaging/java21/audit-runtime-modules.sh" \
        "$application_jar")
[[ "$detected_modules" = "$modules" ]] || {
    echo "Runtime module audit changed." >&2
    echo "Expected: $modules" >&2
    echo "Detected: $detected_modules" >&2
    exit 3
}

"$jdk_root/bin/jpackage" \
    --type app-image \
    --name RomRaider2 \
    --dest "$output_root" \
    --input "$input" \
    --main-jar RomRaider2.jar \
    --main-class com.romraider.ECUExec \
    --app-version 1.1.0 \
    --vendor NatZirt \
    --description "RomRaider2 ECU Studio" \
    --icon "$repo_root/packaging/branding/macos/RomRaider2.icns" \
    --add-modules "$modules" \
    --java-options '-Dromraider2.default.settings.file=$APPDIR/defaults/settings.xml' \
    --java-options '-Dromraider2.customize.dir=$APPDIR/customize' \
    --java-options '-Dromraider2.plugins.dir=$APPDIR/plugins' \
    --java-options '-Dlog4j.configurationFile=$APPDIR/lib/log4j2.xml' \
    --java-options -Dapple.laf.useScreenMenuBar=true \
    --java-options -Dapple.awt.application.name=RomRaider2 \
    --java-options -Dsun.java2d.metal=true \
    --java-options -Xms64M \
    --java-options -Xmx768M

runtime_release=$destination/Contents/runtime/Contents/Home/release
launcher=$destination/Contents/MacOS/RomRaider2
config=$destination/Contents/app/RomRaider2.cfg
[[ -x "$launcher" && -f "$runtime_release" && -f "$config" ]] || {
    echo "The macOS application image is incomplete." >&2
    exit 5
}
grep -q '^JAVA_VERSION="21\.' "$runtime_release"
[[ -f "$destination/Contents/app/$renderer" ]]
for wrong_renderer in \
        org-jetbrains-skiko-skiko-awt-runtime-linux-x64-0.150.1.jar \
        org-jetbrains-skiko-skiko-awt-runtime-windows-x64-0.150.1.jar \
        "$other_macos_renderer"; do
    [[ ! -e "$destination/Contents/app/$wrong_renderer" ]] || {
        echo "Wrong Compose renderer in macOS package: $wrong_renderer" >&2
        exit 5
    }
done
grep -Fq 'romraider2.default.settings.file=$APPDIR/defaults/settings.xml' \
    "$config"
grep -Fq 'romraider2.customize.dir=$APPDIR/customize' "$config"
[[ ! -e "$destination/Contents/app/definitions" ]]
if find "$destination" -type f \( \
        -iname '*.bin' -o -iname '*.hex' -o -iname '*.srf' \) \
        -print -quit | grep -q .; then
    echo "Vehicle ROM content must not be included in the application image." >&2
    exit 5
fi

printf 'macOS %s Java 21 application image: %s\n' \
    "$package_arch" "$destination"
