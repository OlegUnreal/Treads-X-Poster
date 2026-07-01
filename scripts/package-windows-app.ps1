param(
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"

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
