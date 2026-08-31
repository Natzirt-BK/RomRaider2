[CmdletBinding()]
param(
    [string]$OutputRoot
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $RepoRoot "build/j2534-bridge"
}

$Upstream = "https://github.com/mickeyl/j2534-bridge.git"
$Revision = "7234e12c280ae8e91467319a59856f36b81c0e16"
$RomRaiderPatch = Join-Path $RepoRoot "packaging/j2534-bridge/romraider2-j2534-options.patch"
$Cargo = Get-Command cargo -ErrorAction Stop
$Rustup = Get-Command rustup -ErrorAction Stop
$Git = Get-Command git -ErrorAction Stop

if (Test-Path -LiteralPath $OutputRoot) {
    throw "Bridge output already exists; preserving it: $OutputRoot"
}

$Stage = Join-Path ([IO.Path]::GetTempPath()) ("romraider2-j2534-bridge-" + [guid]::NewGuid().ToString("N"))
$Source = Join-Path $Stage "source"
try {
    & $Git.Source clone --quiet --no-checkout $Upstream $Source
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to clone the pinned J2534 bridge source."
    }
    & $Git.Source -C $Source checkout --quiet $Revision
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to check out J2534 bridge revision $Revision."
    }
    $ActualRevision = (& $Git.Source -C $Source rev-parse HEAD).Trim()
    if ($ActualRevision -ne $Revision) {
        throw "J2534 bridge revision mismatch: $ActualRevision"
    }
    $License = Get-Content -LiteralPath (Join-Path $Source "LICENSE") -Raw
    if ($License -notmatch "MIT License" -or $License -notmatch "Copyright \(c\) 2026 mickeyl") {
        throw "The pinned J2534 bridge license did not match the audited MIT record."
    }
    if (-not (Test-Path -LiteralPath $RomRaiderPatch -PathType Leaf)) {
        throw "RomRaider2 J2534 bridge protocol patch is missing: $RomRaiderPatch"
    }
    & $Git.Source -C $Source apply --check $RomRaiderPatch
    if ($LASTEXITCODE -ne 0) {
        throw "The RomRaider2 J2534 bridge protocol patch does not match the pinned source."
    }
    & $Git.Source -C $Source apply $RomRaiderPatch
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to apply the RomRaider2 J2534 bridge protocol patch."
    }

    & $Rustup.Source target add x86_64-pc-windows-msvc i686-pc-windows-msvc
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to install the Rust Windows compilation targets."
    }
    Push-Location $Source
    try {
        & $Cargo.Source test --locked --lib
        if ($LASTEXITCODE -ne 0) {
            throw "The patched J2534 bridge unit suite failed."
        }
        foreach ($Target in @("x86_64-pc-windows-msvc", "i686-pc-windows-msvc")) {
            & $Cargo.Source build --locked --release --target $Target
            if ($LASTEXITCODE -ne 0) {
                throw "J2534 bridge build failed for $Target."
            }
        }
    } finally {
        Pop-Location
    }

    New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
    Copy-Item -LiteralPath (Join-Path $Source "target/i686-pc-windows-msvc/release/j2534-bridge.exe") `
        -Destination (Join-Path $OutputRoot "j2534-bridge-32.exe")
    Copy-Item -LiteralPath (Join-Path $Source "target/x86_64-pc-windows-msvc/release/j2534-bridge.exe") `
        -Destination (Join-Path $OutputRoot "j2534-bridge-64.exe")
    Copy-Item -LiteralPath (Join-Path $Source "LICENSE") `
        -Destination (Join-Path $OutputRoot "LICENSE-j2534-bridge.txt")
    @(
        "Project: j2534-bridge"
        "Repository: $Upstream"
        "Revision: $Revision"
        "Builds: i686-pc-windows-msvc, x86_64-pc-windows-msvc"
        "Cargo mode: --locked --release"
        "RomRaider2 patch: packaging/j2534-bridge/romraider2-j2534-options.patch"
        "Patch scope: hidden helper, raw transmit flags/timeouts, and ISO15765 flow-control data"
    ) | Set-Content -LiteralPath (Join-Path $OutputRoot "SOURCE.txt") -Encoding utf8
} finally {
    if (Test-Path -LiteralPath $Stage) {
        Remove-Item -LiteralPath $Stage -Recurse -Force
    }
}

Write-Host "J2534 cross-bitness helpers: $OutputRoot"
