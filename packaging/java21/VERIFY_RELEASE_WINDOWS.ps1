$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ReleaseRoot = $PSScriptRoot
foreach ($RequiredFile in @(
    "RomRaider2.exe",
    "RomRaider2 Logger.exe",
    "runtime/release",
    "app/RomRaider2.jar",
    "app/lib/windows/64/phidget21.dll",
    "app/lib/windows/j2534/j2534-bridge-32.exe",
    "app/lib/windows/j2534/j2534-bridge-64.exe",
    "app/lib/windows/j2534/SOURCE.txt",
    "app/licenses/j2534-bridge-MIT.txt",
    "config/settings.default.xml",
    "config/user/settings.xml",
    "customize/j2534Libraries.properties"
    "customize/nameSequences.properties"
    "customize/ncslearning.properties"
    "customize/ssmlearning.properties"
    "customize/warningSound.wav"
)) {
    if (-not (Test-Path -LiteralPath (Join-Path $ReleaseRoot $RequiredFile) -PathType Leaf)) {
        throw "Required release file is missing: $RequiredFile"
    }
}

if ((Get-Content -LiteralPath (Join-Path $ReleaseRoot "runtime/release") -Raw) -notmatch 'JAVA_VERSION="21\.') {
    throw "The packaged runtime is not Java 21."
}
if (Test-Path -LiteralPath (Join-Path $ReleaseRoot "definitions")) {
    throw "Vehicle definitions and profiles must not be included in the software release."
}
if (Get-ChildItem -LiteralPath $ReleaseRoot -Recurse -File |
    Where-Object { $_.Extension -in @(".bin", ".hex", ".srf") }) {
    throw "Vehicle ROM content must not be included in the software release."
}
$DefaultSettings = Get-Content -LiteralPath (Join-Path $ReleaseRoot "config/settings.default.xml") -Raw
if ($DefaultSettings -match 'ecudefinitionfile|<definition |<profile ') {
    throw "The default settings select vehicle definitions or profiles."
}
$InitialUserSettings = Get-Content -LiteralPath (Join-Path $ReleaseRoot "config/user/settings.xml") -Raw
if ($InitialUserSettings -ne $DefaultSettings) {
    throw "The initial Windows user settings do not match the neutral release defaults."
}
if ($DefaultSettings -notmatch '<display-preferences[^>]+theme="LIGHT"') {
    throw "The packaged first-run theme is not Light."
}
$LauncherConfig = Get-Content -LiteralPath (Join-Path $ReleaseRoot "app/RomRaider2.cfg") -Raw
if ($LauncherConfig -notmatch [regex]::Escape('romraider2.settings.dir=$APPDIR/../config/user')) {
    throw "The packaged launcher does not isolate RomRaider2 settings."
}
if ($LauncherConfig -notmatch [regex]::Escape('romraider2.customize.dir=$APPDIR/../customize')) {
    throw "The packaged launcher does not locate customization assets."
}
if ($LauncherConfig -notmatch [regex]::Escape('romraider2.log.dir=$APPDIR/../logs')) {
    throw "The packaged launcher does not isolate RomRaider2 logs."
}
if ($LauncherConfig -notmatch [regex]::Escape('log4j.configurationFile=$APPDIR/lib/log4j2.xml')) {
    throw "The packaged launcher does not select its Log4j configuration."
}
if ($LauncherConfig -notmatch [regex]::Escape('romraider2.j2534.bridge.dir=$APPDIR/lib/windows/j2534')) {
    throw "The packaged launcher does not locate the J2534 architecture bridges."
}
foreach ($RetiredDependency in @(
    "Graph3d.jar", "j3dcore.jar", "j3dutils.jar", "vecmath.jar",
    "j3dcore-d3d.dll", "j3dcore-ogl-cg.dll", "j3dcore-ogl-chk.dll", "j3dcore-ogl.dll"
)) {
    if (Get-ChildItem -LiteralPath $ReleaseRoot -Recurse -File -Filter $RetiredDependency) {
        throw "Retired Java 3D dependency was packaged: $RetiredDependency"
    }
}

$ChecksumFile = Join-Path $ReleaseRoot "checksums/SHA256SUMS.txt"
if (Test-Path -LiteralPath $ChecksumFile -PathType Leaf) {
    foreach ($Line in Get-Content -LiteralPath $ChecksumFile) {
        if ($Line -notmatch "^([0-9a-f]{64})\s{2}(.+)$") {
            throw "Invalid checksum entry: $Line"
        }
        $Expected = $Matches[1]
        $RelativePath = $Matches[2]
        $File = Join-Path $ReleaseRoot $RelativePath
        if (-not (Test-Path -LiteralPath $File -PathType Leaf)) {
            throw "Checksummed release file is missing: $RelativePath"
        }
        $Actual = (Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($Actual -ne $Expected) {
            throw "Checksum mismatch: $RelativePath"
        }
    }
}

Write-Host "RomRaider2 Windows release verified."
