[CmdletBinding()]
param(
    [string]$OutputRoot,
    [string]$ApplicationJar
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $RepoRoot "build/java21-windows"
}
if (-not $ApplicationJar) {
    $ApplicationJar = Join-Path $RepoRoot "build/windows/lib/RomRaider2.jar"
}

$JdkRoot = $env:JAVA_HOME
if (-not $JdkRoot) {
    throw "Set JAVA_HOME to a Java 21 x64 JDK."
}

$Java = Join-Path $JdkRoot "bin/java.exe"
$Javap = Join-Path $JdkRoot "bin/javap.exe"
$Jdeps = Join-Path $JdkRoot "bin/jdeps.exe"
$Jpackage = Join-Path $JdkRoot "bin/jpackage.exe"
foreach ($Tool in @($Java, $Javap, $Jdeps, $Jpackage)) {
    if (-not (Test-Path -LiteralPath $Tool -PathType Leaf)) {
        throw "JAVA_HOME does not contain required tool: $Tool"
    }
}

$JavaSettings = (& $Java -XshowSettings:properties -version 2>&1) -join "`n"
if ($JavaSettings -notmatch "java\.specification\.version\s*=\s*21(?:\s|$)") {
    throw "A Java 21 JDK is required: $JdkRoot"
}
if ($JavaSettings -notmatch "os\.arch\s*=\s*(amd64|x86_64)(?:\s|$)") {
    throw "A Windows x64 JDK is required: $JdkRoot"
}
if (-not (Test-Path -LiteralPath $ApplicationJar -PathType Leaf)) {
    throw "Build RomRaider2 for Windows first; jar not found: $ApplicationJar"
}

$ClassVersion = (& $Javap -verbose -classpath $ApplicationJar com.romraider.ECUExec |
    Select-String -Pattern "major version:\s*(\d+)" |
    Select-Object -First 1).Matches.Groups[1].Value
if ($ClassVersion -ne "65") {
    throw "RomRaider2.jar is not Java 21 bytecode (major version 65): $ClassVersion"
}

$LegacyGraphReferences = (& $Jdeps --multi-release 21 --ignore-missing-deps `
    -verbose:class $ApplicationJar 2>$null | Select-String -Pattern "com\.ecm\.")
if ($LegacyGraphReferences) {
    throw "The Java 21 application still links to the retired Graph3d API."
}

$DependencyManifest = Join-Path $RepoRoot "packaging/java21/audited-dependencies.sha256"
if (-not (Test-Path -LiteralPath $DependencyManifest -PathType Leaf)) {
    throw "Audited dependency manifest is missing: $DependencyManifest"
}
$VerifiedDependencies = 0
foreach ($Line in Get-Content -LiteralPath $DependencyManifest) {
    if (-not $Line -or $Line.StartsWith("#")) {
        continue
    }
    if ($Line -notmatch "^([0-9a-f]{64})\s+(.+)$") {
        throw "Invalid audited dependency entry: $Line"
    }
    $Expected = $Matches[1]
    $RelativePath = $Matches[2]
    $Dependency = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $Dependency -PathType Leaf)) {
        throw "Audited dependency is missing: $RelativePath"
    }
    $Actual = (Get-FileHash -LiteralPath $Dependency -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Actual -ne $Expected) {
        throw "Audited dependency hash mismatch: $RelativePath`nExpected: $Expected`nActual:   $Actual"
    }
    $VerifiedDependencies++
}
if ($VerifiedDependencies -eq 0) {
    throw "Audited dependency manifest contains no artifacts."
}
Write-Host "Verified $VerifiedDependencies audited release dependencies."

foreach ($ObsoleteDependency in @("log4j-1.2.14.jar", "jSerialComm-2.9.1.jar")) {
    if (Test-Path -LiteralPath (Join-Path $RepoRoot "lib/common/$ObsoleteDependency")) {
        throw "Obsolete dependency must not be packaged: $ObsoleteDependency"
    }
}

$Destination = Join-Path $OutputRoot "RomRaider2"
if (Test-Path -LiteralPath $Destination) {
    throw "Destination already exists; preserving it: $Destination"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

$Stage = Join-Path ([IO.Path]::GetTempPath()) ("romraider2-jpackage-" + [guid]::NewGuid().ToString("N"))
$InputRoot = Join-Path $Stage "input"
$LoggerProperties = Join-Path $Stage "logger.properties"
try {
    foreach ($Directory in @(
        $InputRoot,
        (Join-Path $InputRoot "lib/common"),
        (Join-Path $InputRoot "lib/windows/64"),
        (Join-Path $InputRoot "lib"),
        (Join-Path $InputRoot "plugins"),
        (Join-Path $InputRoot "licenses")
    )) {
        New-Item -ItemType Directory -Force -Path $Directory | Out-Null
    }

    Copy-Item -LiteralPath $ApplicationJar -Destination (Join-Path $InputRoot "RomRaider2.jar")
    Get-ChildItem -LiteralPath (Join-Path $RepoRoot "lib/common") -Filter "*.jar" -File |
        Where-Object { $_.Name -notin @("Graph3d.jar", "j3dcore.jar", "j3dutils.jar", "vecmath.jar") } |
        Copy-Item -Destination (Join-Path $InputRoot "lib/common")
    Copy-Item -LiteralPath (Join-Path $RepoRoot "lib/windows/64/phidget21.dll") `
        -Destination (Join-Path $InputRoot "lib/windows/64/phidget21.dll")
    Copy-Item -LiteralPath (Join-Path $RepoRoot "lib/log4j2.xml") `
        -Destination (Join-Path $InputRoot "lib/log4j2.xml")
    Copy-Item -Path (Join-Path $RepoRoot "plugins/*.plugin") `
        -Destination (Join-Path $InputRoot "plugins")
    Copy-Item -Path (Join-Path $RepoRoot "licenses/*") `
        -Destination (Join-Path $InputRoot "licenses")

    $ClassPathEntries = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot "lib/common") `
        -Filter "*.jar" -File | ForEach-Object { $_.FullName })
    if ($ClassPathEntries.Count -eq 0) {
        throw "No audited Java dependencies were found for the runtime module audit."
    }
    $ClassPath = [string]::Join([IO.Path]::PathSeparator, $ClassPathEntries)
    $JdepsOutput = @(& $Jdeps --multi-release 21 --ignore-missing-deps `
        --print-module-deps --class-path $ClassPath $ApplicationJar 2>&1)
    $JdepsStatus = $LASTEXITCODE
    if ($JdepsStatus -ne 0) {
        throw "jdeps runtime module audit failed with exit code $JdepsStatus.`n$($JdepsOutput -join "`n")"
    }
    $DetectedModules = ($JdepsOutput | Where-Object { $_ } |
        Select-Object -Last 1).ToString().Trim()
    $ExpectedModules = "java.base,java.compiler,java.desktop,java.management,java.naming,java.rmi,java.scripting,java.sql,jdk.unsupported"
    if ($DetectedModules -ne $ExpectedModules) {
        throw "Runtime module audit changed.`nExpected: $ExpectedModules`nDetected: $DetectedModules"
    }

    $WindowsIcon = (Join-Path $RepoRoot "packaging/branding/windows/RomRaider2.ico").Replace('\', '/')
    @(
        "arguments=-logger"
        "icon=$WindowsIcon"
    ) | Set-Content -LiteralPath $LoggerProperties -Encoding utf8

    & $Jpackage `
        --type app-image `
        --name RomRaider2 `
        --dest $OutputRoot `
        --input $InputRoot `
        --main-jar RomRaider2.jar `
        --main-class com.romraider.ECUExec `
        --app-version 1.1.0 `
        --vendor NatZirt `
        --description "RomRaider2 ECU Studio" `
        --icon (Join-Path $RepoRoot "packaging/branding/windows/RomRaider2.ico") `
        --add-launcher "RomRaider2 Logger=$LoggerProperties" `
        --add-modules $ExpectedModules `
        --java-options '-Djava.library.path=$APPDIR/lib/windows/64' `
        --java-options '-Dromraider2.plugins.dir=$APPDIR/plugins' `
        --java-options '-Dromraider2.settings.dir=$APPDIR/../config/user' `
        --java-options '-Dromraider2.customize.dir=$APPDIR/../customize' `
        --java-options '-Dromraider2.log.dir=$APPDIR/../logs' `
        --java-options '-Dlog4j.configurationFile=$APPDIR/lib/log4j2.xml' `
        --java-options '-Dawt.useSystemAAFontSettings=lcd' `
        --java-options '-Dswing.aatext=true' `
        --java-options '-Dsun.java2d.d3d=true' `
        --java-options '-Xms64M' `
        --java-options '-Xmx768M'
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE"
    }
} finally {
    if (Test-Path -LiteralPath $Stage) {
        Remove-Item -LiteralPath $Stage -Recurse -Force
    }
}

$RuntimeRelease = Join-Path $Destination "runtime/release"
if (-not (Test-Path -LiteralPath $RuntimeRelease -PathType Leaf)) {
    throw "Packaged runtime metadata is missing: $RuntimeRelease"
}
if ((Get-Content -LiteralPath $RuntimeRelease -Raw) -notmatch 'JAVA_VERSION="21\.') {
    throw "Packaged runtime is not Java 21: $RuntimeRelease"
}

foreach ($Directory in @("config", "config/user", "customize", "logs", "roms", "repositories")) {
    New-Item -ItemType Directory -Force -Path (Join-Path $Destination $Directory) | Out-Null
}
Copy-Item -LiteralPath (Join-Path $RepoRoot "packaging/default/settings.xml") `
    -Destination (Join-Path $Destination "config/settings.default.xml")
Copy-Item -LiteralPath (Join-Path $RepoRoot "packaging/default/settings.xml") `
    -Destination (Join-Path $Destination "config/user/settings.xml")
Copy-Item -Path (Join-Path $RepoRoot "customize/*") `
    -Destination (Join-Path $Destination "customize")

$VehicleFiles = Get-ChildItem -LiteralPath $Destination -Recurse -File |
    Where-Object { $_.Extension -in @(".bin", ".hex", ".srf") }
if ($VehicleFiles) {
    throw "Vehicle ROM content must not be included in the software release."
}
if (Test-Path -LiteralPath (Join-Path $Destination "definitions")) {
    throw "Vehicle definitions and profiles must not be included in the software release."
}
$DefaultSettings = Get-Content -LiteralPath (Join-Path $Destination "config/settings.default.xml") -Raw
if ($DefaultSettings -match 'ecudefinitionfile|<definition |<profile ') {
    throw "The default settings must not select vehicle definitions or profiles."
}
$InitialUserSettings = Get-Content -LiteralPath (Join-Path $Destination "config/user/settings.xml") -Raw
if ($InitialUserSettings -ne $DefaultSettings) {
    throw "The initial Windows user settings must match the neutral release defaults."
}
$LauncherConfig = Get-Content -LiteralPath (Join-Path $Destination "app/RomRaider2.cfg") -Raw
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
foreach ($Customization in @(
    "nameSequences.properties", "ncslearning.properties",
    "ssmlearning.properties", "warningSound.wav"
)) {
    if (-not (Test-Path -LiteralPath (Join-Path $Destination "customize/$Customization") -PathType Leaf)) {
        throw "Required customization asset is missing: $Customization"
    }
}
foreach ($RetiredDependency in @(
    "Graph3d.jar", "j3dcore.jar", "j3dutils.jar", "vecmath.jar",
    "j3dcore-d3d.dll", "j3dcore-ogl-cg.dll", "j3dcore-ogl-chk.dll", "j3dcore-ogl.dll"
)) {
    if (Get-ChildItem -LiteralPath $Destination -Recurse -File -Filter $RetiredDependency) {
        throw "Retired Java 3D dependency was packaged: $RetiredDependency"
    }
}

Write-Host "Java 21 Windows application image: $Destination"
