#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
manifest=$repo_root/packaging/java21/audited-dependencies.sha256

command -v sha256sum >/dev/null || {
    echo "sha256sum is required to verify release dependencies." >&2
    exit 1
}
[[ -f "$manifest" ]] || {
    echo "Audited dependency manifest is missing: $manifest" >&2
    exit 1
}

verified=0
while read -r expected relative_path; do
    [[ -n "${expected:-}" && "${expected:0:1}" != "#" ]] || continue
    [[ "$expected" =~ ^[0-9a-f]{64}$ && -n "${relative_path:-}" ]] || {
        echo "Invalid audited dependency entry: $expected ${relative_path:-}" >&2
        exit 2
    }
    dependency=$repo_root/$relative_path
    [[ -f "$dependency" ]] || {
        echo "Audited dependency is missing: $relative_path" >&2
        exit 2
    }
    actual=$(sha256sum -- "$dependency")
    actual=${actual%% *}
    [[ "$actual" = "$expected" ]] || {
        echo "Audited dependency hash mismatch: $relative_path" >&2
        echo "Expected: $expected" >&2
        echo "Actual:   $actual" >&2
        exit 2
    }
    verified=$((verified + 1))
done < "$manifest"

[[ "$verified" -gt 0 ]] || {
    echo "Audited dependency manifest contains no artifacts." >&2
    exit 2
}
printf 'Verified %d audited release dependencies.\n' "$verified"
