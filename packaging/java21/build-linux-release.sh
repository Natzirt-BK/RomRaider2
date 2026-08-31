#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
application_image=${ROMRAIDER2_APP_IMAGE:-$repo_root/build/release-candidate-1.1.0/RomRaider2}
output_root=${1:-$repo_root/build/releases}
release_name=${ROMRAIDER2_RELEASE_NAME:-RomRaider2_ECU_Studio_1.1.0_Linux_x64}
destination=$output_root/$release_name
archive=$output_root/$release_name.zip
archive_name=${archive##*/}

[[ -x "$application_image/bin/RomRaider2" && \
   -f "$application_image/lib/runtime/release" ]] || {
    echo "Build the Java 21 application image first: $application_image" >&2
    exit 1
}
[[ ! -e "$destination" && ! -e "$archive" ]] || {
    echo "Release output already exists; preserving it: $destination" >&2
    exit 2
}

mkdir -p "$output_root"
stage=$(mktemp -d "$output_root/.romraider2-release.XXXXXX")
trap 'rm -rf -- "$stage"' EXIT HUP INT TERM
release=$stage/$release_name
cp -a -- "$application_image" "$release"
mkdir -p "$release/docs" "$release/checksums"
cp -- "$repo_root/README.md" "$release/README.md"
cp -- "$repo_root/release_notes.txt" "$release/RELEASE_NOTES.txt"
cp -- "$repo_root/docs/ROMRAIDER2_IMPLEMENTATION_STATUS.md" "$release/docs/"
cp -- "$repo_root/docs/JAVA_RUNTIME_MODERNIZATION.md" "$release/docs/"
cp -- "$repo_root/docs/DIAGNOSTIC_PRIVACY.md" "$release/docs/"
cp -- "$repo_root/packaging/java21/VERIFY_RELEASE_LINUX.sh" "$release/"
chmod +x "$release/VERIFY_RELEASE_LINUX.sh"

commit=$(git -C "$repo_root" rev-parse HEAD)
{
    printf 'RomRaider2 ECU Studio 1.1.0 Release Candidate 2\n'
    printf 'Source commit: %s\n' "$commit"
    printf 'Built: %s\n' "$(date --iso-8601=seconds)"
    printf 'Runtime: Java 21 x64 application image\n'
} >"$release/VERSION.txt"

(
    cd "$release"
    find . -type f ! -path './checksums/SHA256SUMS.txt' -print0 | \
        sort -z | xargs -0 sha256sum >checksums/SHA256SUMS.txt
)
"$release/VERIFY_RELEASE_LINUX.sh"

mv -- "$release" "$destination"
rmdir -- "$stage"
trap - EXIT HUP INT TERM
(
    cd "$output_root"
    if command -v zip >/dev/null 2>&1; then
        zip -qr "$archive_name" "$release_name"
    elif command -v 7z >/dev/null 2>&1; then
        7z a -tzip -mx=7 "$archive_name" "$release_name" >/dev/null
    elif command -v bsdtar >/dev/null 2>&1; then
        bsdtar -a -cf "$archive_name" "$release_name"
    else
        echo "Install zip, 7z, or bsdtar to create the release archive." >&2
        exit 1
    fi
)
(
    cd "$output_root"
    sha256sum "$archive_name" >"$archive_name.sha256"
)

printf 'RomRaider2 release folder: %s\n' "$destination"
printf 'RomRaider2 release archive: %s\n' "$archive"
