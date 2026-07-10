param(
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$launcherDir = Split-Path -Parent $PSScriptRoot
$workspaceDir = Split-Path -Parent $launcherDir
$modDir = Join-Path $workspaceDir 'mod'

$gradle = Get-Command gradle.bat, gradle -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source
if (-not $gradle) {
    $wrapperCache = Join-Path $HOME '.gradle\wrapper\dists'
    $gradle = Get-ChildItem -LiteralPath $wrapperCache -Filter gradle.bat -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $gradle) {
    throw 'Gradle 9.x was not found. Install Gradle or add a Gradle wrapper before building the installer.'
}

Push-Location $modDir
try {
    & $gradle clean build
    if ($LASTEXITCODE -ne 0) { throw 'The Veyra mod build failed.' }
} finally {
    Pop-Location
}

Push-Location $launcherDir
try {
    if (-not $SkipInstall) {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'npm dependency installation failed.' }
    }

    & npm.cmd run tauri build -- --bundles nsis
    if ($LASTEXITCODE -ne 0) { throw 'The Tauri NSIS build failed.' }
} finally {
    Pop-Location
}

$bundleDir = Join-Path $launcherDir 'src-tauri\target\release\bundle\nsis'
$installer = Get-ChildItem -LiteralPath $bundleDir -Filter '*-setup.exe' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $installer) {
    throw "No NSIS installer was found in $bundleDir"
}

Write-Host "Installer ready: $($installer.FullName)"
