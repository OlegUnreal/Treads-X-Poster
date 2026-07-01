param(
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"
$runtimeDir = Join-Path $frontendDir "runtime"
$javaRuntimeDir = Join-Path $runtimeDir "java"
$pythonRuntimeDir = Join-Path $runtimeDir "python"
$pythonVersion = "3.12.8"
$pythonZip = Join-Path $runtimeDir "python-$pythonVersion-embed-amd64.zip"

mvn -f (Join-Path $backendDir "pom.xml") package -DskipTests

$jar = Get-ChildItem -Path (Join-Path $backendDir "target") -Filter "*.jar" |
    Where-Object { $_.Name -ne "app.jar" -and $_.Name -notlike "*.original" -and $_.Name -notlike "*sources.jar" -and $_.Name -notlike "*javadoc.jar" } |
    Select-Object -First 1

if (-not $jar) {
    throw "Backend jar was not found."
}

$appJar = Join-Path $backendDir "target\app.jar"
if ((Resolve-Path $jar.FullName).Path -ne (Resolve-Path $appJar -ErrorAction SilentlyContinue).Path) {
    Copy-Item -LiteralPath $jar.FullName -Destination $appJar -Force
}

Push-Location $frontendDir
try {
    if (-not $SkipInstall) {
        npm install
    }
    New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

    if (Test-Path -LiteralPath $javaRuntimeDir) {
        Remove-Item -LiteralPath $javaRuntimeDir -Recurse -Force
    }
    if (-not $env:JAVA_HOME) {
        throw "JAVA_HOME is required to build a self-contained Windows app runtime."
    }
    $jlink = Join-Path $env:JAVA_HOME "bin\jlink.exe"
    $jmods = Join-Path $env:JAVA_HOME "jmods"
    if (-not (Test-Path -LiteralPath $jlink)) {
        throw "jlink.exe was not found under JAVA_HOME: $env:JAVA_HOME"
    }
    if (-not (Test-Path -LiteralPath $jmods)) {
        throw "JDK jmods directory was not found under JAVA_HOME: $env:JAVA_HOME"
    }
    & $jlink `
        --module-path $jmods `
        --add-modules ALL-MODULE-PATH `
        --strip-debug `
        --no-header-files `
        --no-man-pages `
        --compress=2 `
        --output $javaRuntimeDir
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create bundled Java runtime."
    }

    if (Test-Path -LiteralPath $pythonRuntimeDir) {
        Remove-Item -LiteralPath $pythonRuntimeDir -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $pythonRuntimeDir | Out-Null
    if (-not (Test-Path -LiteralPath $pythonZip)) {
        $pythonUrl = "https://www.python.org/ftp/python/$pythonVersion/python-$pythonVersion-embed-amd64.zip"
        Invoke-WebRequest -UseBasicParsing -Uri $pythonUrl -OutFile $pythonZip
    }
    Expand-Archive -LiteralPath $pythonZip -DestinationPath $pythonRuntimeDir -Force
    if (-not (Test-Path -LiteralPath (Join-Path $pythonRuntimeDir "python.exe"))) {
        throw "Bundled Python runtime was not created."
    }

    npm run build
    npm run package:win
} finally {
    Pop-Location
}

$exe = Join-Path $frontendDir "release\BehindTheSmilePlayback.exe"
if (-not (Test-Path -LiteralPath $exe)) {
    throw "Expected exe was not created: $exe"
}

Write-Host "Windows app created: $exe"
