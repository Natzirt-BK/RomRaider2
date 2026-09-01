[CmdletBinding()]
param(
    [string]$ApplicationImage,
    [string]$OutputRoot,
    [string]$ReleaseName = "RomRaider2_ECU_Studio_1.1.0_Windows_x64",
    [string]$ReleaseLabel = "Release Candidate 3",
    [string]$SourceRevision
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
if (-not $ApplicationImage) {
    $ApplicationImage = Join-Path $RepoRoot "build/java21-windows/RomRaider2"
}
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $RepoRoot "build/releases-windows"
}
$GitCommand = Get-Command git -ErrorAction SilentlyContinue
$GitRoot = $null
$IsRepositoryCheckout = $false
if ($GitCommand) {
    $GitRoot = (& $GitCommand.Source -C $RepoRoot rev-parse --show-toplevel 2>$null)
    $IsRepositoryCheckout = $LASTEXITCODE -eq 0 -and $GitRoot -and
        (Resolve-Path $GitRoot).Path -eq $RepoRoot
}
if (-not $SourceRevision) {
    $SourceRevision = $env:ROMRAIDER2_SOURCE_REVISION
}
if (-not $SourceRevision) {
    if ($IsRepositoryCheckout) {
        $SourceRevision = (& $GitCommand.Source -C $RepoRoot rev-parse HEAD).Trim()
    } else {
        throw "Set ROMRAIDER2_SOURCE_REVISION when packaging an extracted source archive."
    }
}
if ($SourceRevision -notmatch '^[0-9a-f]{40}$') {
    throw "Invalid RomRaider2 source revision: $SourceRevision"
}
if ($IsRepositoryCheckout) {
    $HeadRevision = (& $GitCommand.Source -C $RepoRoot rev-parse HEAD).Trim()
    if ($SourceRevision -ne $HeadRevision) {
        throw "ROMRAIDER2_SOURCE_REVISION does not match HEAD: $HeadRevision"
    }
    $DirtySource = @(& $GitCommand.Source -C $RepoRoot status --porcelain --untracked-files=normal)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to verify the RomRaider2 source tree state."
    }
    if ($DirtySource.Count -gt 0) {
        throw "Refusing to package a dirty source tree."
    }
}

if (-not (Test-Path -LiteralPath (Join-Path $ApplicationImage "RomRaider2.exe") -PathType Leaf) -or
    -not (Test-Path -LiteralPath (Join-Path $ApplicationImage "runtime/release") -PathType Leaf)) {
    throw "Build the Java 21 Windows application image first: $ApplicationImage"
}

$Destination = Join-Path $OutputRoot $ReleaseName
$Archive = Join-Path $OutputRoot "$ReleaseName.zip"
$ArchiveHash = "$Archive.sha256"
foreach ($Path in @($Destination, $Archive, $ArchiveHash)) {
    if (Test-Path -LiteralPath $Path) {
        throw "Release output already exists; preserving it: $Path"
    }
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

$Stage = Join-Path $OutputRoot (".romraider2-release-" + [guid]::NewGuid().ToString("N"))
$Release = Join-Path $Stage $ReleaseName
try {
    Copy-Item -LiteralPath $ApplicationImage -Destination $Release -Recurse
    New-Item -ItemType Directory -Force -Path (Join-Path $Release "docs") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $Release "checksums") | Out-Null
    Copy-Item -LiteralPath (Join-Path $RepoRoot "README.md") -Destination (Join-Path $Release "README.md")
    Copy-Item -LiteralPath (Join-Path $RepoRoot "release_notes.txt") -Destination (Join-Path $Release "RELEASE_NOTES.txt")
    Copy-Item -LiteralPath (Join-Path $RepoRoot "docs/ROMRAIDER2_IMPLEMENTATION_STATUS.md") -Destination (Join-Path $Release "docs")
    Copy-Item -LiteralPath (Join-Path $RepoRoot "docs/JAVA_RUNTIME_MODERNIZATION.md") -Destination (Join-Path $Release "docs")
    Copy-Item -LiteralPath (Join-Path $RepoRoot "docs/RC3_RELEASE_READINESS.md") -Destination (Join-Path $Release "docs")
    Copy-Item -LiteralPath (Join-Path $RepoRoot "docs/RC3_QUALIFICATION_RECORD.md") -Destination (Join-Path $Release "docs")
    Copy-Item -LiteralPath (Join-Path $RepoRoot "docs/WINDOWS_RELEASE_CHECKLIST.md") -Destination (Join-Path $Release "docs")
    Copy-Item -LiteralPath (Join-Path $RepoRoot "docs/DIAGNOSTIC_PRIVACY.md") -Destination (Join-Path $Release "docs")
    Copy-Item -LiteralPath (Join-Path $RepoRoot "packaging/java21/VERIFY_RELEASE_WINDOWS.ps1") -Destination $Release

    @(
        "RomRaider2 ECU Studio 1.1.0 $ReleaseLabel"
        "Source commit: $SourceRevision"
        "Built: $([DateTimeOffset]::Now.ToString('o'))"
        "Runtime: Java 21 Windows x64 application image"
        "Qualification: Preview; Windows hardware validation pending"
    ) | Set-Content -LiteralPath (Join-Path $Release "VERSION.txt") -Encoding utf8

    $ChecksumFile = Join-Path $Release "checksums/SHA256SUMS.txt"
    Get-ChildItem -LiteralPath $Release -Recurse -File |
        Where-Object { $_.FullName -ne $ChecksumFile } |
        Sort-Object FullName |
        ForEach-Object {
            $Relative = [IO.Path]::GetRelativePath($Release, $_.FullName).Replace('\', '/')
            $Hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            "$Hash  $Relative"
        } | Set-Content -LiteralPath $ChecksumFile -Encoding utf8

    & (Join-Path $Release "VERIFY_RELEASE_WINDOWS.ps1")

    Move-Item -LiteralPath $Release -Destination $Destination
} finally {
    if (Test-Path -LiteralPath $Stage) {
        Remove-Item -LiteralPath $Stage -Recurse -Force
    }
}

Compress-Archive -LiteralPath $Destination -DestinationPath $Archive -CompressionLevel Optimal
$Hash = (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash.ToLowerInvariant()
"$Hash  $([IO.Path]::GetFileName($Archive))" | Set-Content -LiteralPath $ArchiveHash -Encoding ascii

Write-Host "RomRaider2 Windows release folder: $Destination"
Write-Host "RomRaider2 Windows release archive: $Archive"
