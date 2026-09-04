#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
jdk_root=${JAVA_HOME:?Set JAVA_HOME to a Java 21 JDK}
application_jar=${ROMRAIDER2_JAR:-$repo_root/build/linux/lib/RomRaider2.jar}
javafx_root=$repo_root/build/javafx/linux
output_root=${1:-$repo_root/build/java21}
image_name=RomRaider2
destination=$output_root/$image_name
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
    echo "Build RomRaider2 first; jar not found: $application_jar" >&2
    exit 1
}
[[ -f "$javafx_root/romraider2-javafx-desktop-1.1.0-rc4.jar" ]] || {
    echo "Stage the JavaFX desktop workspace before packaging." >&2
    exit 1
}
class_version=$(
    "$jdk_root/bin/javap" -verbose -classpath "$application_jar" \
        com.romraider.ECUExec | sed -n 's/^[[:space:]]*major version: //p'
)
[[ "$class_version" = 65 ]] || {
    echo "RomRaider2.jar is not Java 21 bytecode (major version 65): $class_version" >&2
    echo "Run a clean Java 21 build before packaging." >&2
    exit 4
}
legacy_graph_references=$(
    "$jdk_root/bin/jdeps" --multi-release 21 --ignore-missing-deps \
        -verbose:class "$application_jar" 2>/dev/null | \
        grep 'com\.ecm\.' || true
)
[[ -z "$legacy_graph_references" ]] || {
    echo "The Java 21 application still links to the retired Graph3d API:" >&2
    printf '%s\n' "$legacy_graph_references" >&2
    exit 4
}
[[ -d "$repo_root/lib/common" && -d "$repo_root/lib/linux/64" ]] || {
    echo "Required application libraries are missing." >&2
    exit 1
}
"$repo_root/packaging/java21/verify-audited-dependencies.sh"
for obsolete_dependency in log4j-1.2.14.jar jSerialComm-2.9.1.jar; do
    [[ ! -e "$repo_root/lib/common/$obsolete_dependency" ]] || {
        echo "Obsolete dependency must not be packaged: $obsolete_dependency" >&2
        exit 4
    }
done
[[ ! -e "$destination" ]] || {
    echo "Destination already exists; preserving it: $destination" >&2
    exit 2
}

mkdir -p "$output_root"
stage=$(mktemp -d "${TMPDIR:-/tmp}/romraider2-jpackage.XXXXXX")
trap 'rm -rf -- "$stage"' EXIT HUP INT TERM
input=$stage/input
mkdir -p "$input/lib/common" "$input/lib/linux/64" "$input/lib" \
    "$input/plugins" "$input/licenses"
cp -- "$application_jar" "$input/RomRaider2.jar"
cp -- "$javafx_root"/*.jar "$input/"
for dependency in "$repo_root"/lib/common/*.jar; do
    case "${dependency##*/}" in
        Graph3d.jar|j3dcore.jar|j3dutils.jar|vecmath.jar) continue ;;
    esac
    cp -- "$dependency" "$input/lib/common/"
done
for native_library in "$repo_root"/lib/linux/64/*.so; do
    case "${native_library##*/}" in
        libj3dcore-ogl.so) continue ;;
    esac
    cp -- "$native_library" "$input/lib/linux/64/"
done
cp -- "$repo_root/lib/log4j2.xml" "$input/lib/log4j2.xml"
cp -- "$repo_root"/plugins/*.plugin "$input/plugins/"
cp -- "$repo_root"/licenses/* "$input/licenses/"
cp -- "$repo_root/license.txt" "$input/licenses/GPL-2.0.txt"

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
    --name "$image_name" \
    --dest "$output_root" \
    --input "$input" \
    --main-jar RomRaider2.jar \
    --main-class com.romraider.ECUExec \
    --app-version 1.1.0 \
    --vendor NatZirt \
    --description "RomRaider2 ECU Studio" \
    --icon "$repo_root/packaging/branding/linux/hicolor/128x128/apps/romraider2.png" \
    --add-modules "$modules" \
    --java-options '-Djava.library.path=$APPDIR/lib/linux/64' \
    --java-options '-Dromraider2.j2534.library=$APPDIR/lib/linux/64/j2534.so' \
    --java-options '-Dromraider2.plugins.dir=$APPDIR/plugins' \
    --java-options '-Dromraider2.settings.dir=$APPDIR/../../config/user' \
    --java-options '-Dromraider2.customize.dir=$APPDIR/../../customize' \
    --java-options '-Dromraider2.log.dir=$APPDIR/../../logs' \
    --java-options -Dromraider2.desktop.shell=javafx \
    --java-options '--module-path=$APPDIR' \
    --java-options --add-modules=javafx.controls \
    --java-options '-Dlog4j.configurationFile=$APPDIR/lib/log4j2.xml' \
    --java-options -Dawt.useSystemAAFontSettings=lcd \
    --java-options -Dswing.aatext=true \
    --java-options -Dsun.java2d.d3d=false \
    --java-options -Xms64M \
    --java-options -Xmx768M

runtime_release=$destination/lib/runtime/release
[[ -f "$runtime_release" ]] || {
    echo "Packaged runtime metadata is missing: $runtime_release" >&2
    exit 5
}
grep -q '^JAVA_VERSION="21\.' "$runtime_release" || {
    echo "Packaged runtime is not Java 21: $runtime_release" >&2
    exit 5
}

mkdir -p "$destination/config/user" "$destination/customize" \
    "$destination/logs" "$destination/roms" "$destination/repositories"
cp -- "$repo_root/packaging/default/settings.xml" \
    "$destination/config/settings.default.xml"
cp -- "$repo_root/packaging/default/settings.xml" \
    "$destination/config/user/settings.xml"
cp -- "$repo_root"/customize/* "$destination/customize/"
if find "$destination" -type f \( \
        -iname '*.bin' -o -iname '*.hex' -o -iname '*.srf' \) -print -quit | \
        grep -q .; then
    echo "Vehicle ROM content must not be included in the software release." >&2
    exit 5
fi
[[ ! -e "$destination/definitions" ]] || {
    echo "Vehicle definitions and profiles must not be included in the software release." >&2
    exit 5
}
if grep -Eq 'ecudefinitionfile|<definition |<profile ' \
        "$destination/config/settings.default.xml"; then
    echo "The default settings must not select vehicle definitions or profiles." >&2
    exit 5
fi
cmp -s "$destination/config/settings.default.xml" \
        "$destination/config/user/settings.xml" || {
    echo "The initial Linux user settings must match the neutral release defaults." >&2
    exit 5
}
grep -Fq 'linux=j2534.so' \
    "$destination/customize/j2534Libraries.properties" || {
    echo "The packaged J2534 discovery metadata is invalid." >&2
    exit 5
}
grep -Fq 'romraider2.settings.dir=$APPDIR/../../config/user' \
    "$destination/lib/app/RomRaider2.cfg" || {
    echo "The packaged launcher does not isolate RomRaider2 settings." >&2
    exit 5
}
grep -Fq 'romraider2.customize.dir=$APPDIR/../../customize' \
    "$destination/lib/app/RomRaider2.cfg" || {
    echo "The packaged launcher does not locate customization assets." >&2
    exit 5
}
grep -Fq 'romraider2.log.dir=$APPDIR/../../logs' \
    "$destination/lib/app/RomRaider2.cfg" || {
    echo "The packaged launcher does not isolate RomRaider2 logs." >&2
    exit 5
}
grep -Fq 'log4j.configurationFile=$APPDIR/lib/log4j2.xml' \
    "$destination/lib/app/RomRaider2.cfg" || {
    echo "The packaged launcher does not select its Log4j configuration." >&2
    exit 5
}
for customization in nameSequences.properties ncslearning.properties \
        ssmlearning.properties warningSound.wav; do
    [[ -f "$destination/customize/$customization" ]] || {
        echo "Required customization asset is missing: $customization" >&2
        exit 5
    }
done
for javafx_notice in JavaFX-21.0.10-GPL-2.0-Classpath-Exception.txt; do
    [[ -f "$destination/lib/app/licenses/$javafx_notice" ]] || {
        echo "Required JavaFX license file is missing: $javafx_notice" >&2
        exit 5
    }
done
for retired_dependency in \
        Graph3d.jar j3dcore.jar j3dutils.jar vecmath.jar libj3dcore-ogl.so; do
    if find "$destination/lib/app" -type f -name "$retired_dependency" -print -quit | \
            grep -q .; then
        echo "Retired Java 3D dependency was packaged: $retired_dependency" >&2
        exit 5
    fi
done

printf 'Java 21 application image: %s\n' "$destination"
