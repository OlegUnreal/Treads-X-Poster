param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot "frontend"
$backendDir = Join-Path $repoRoot "backend"

if (-not $SkipBuild) {
    mvn -f (Join-Path $backendDir "pom.xml") package -DskipTests

    Push-Location $frontendDir
    try {
        if (Test-Path -LiteralPath "package-lock.json") {
            npm install
        } else {
            npm install --no-package-lock
        }
        npm run build
    } finally {
        Pop-Location
    }
}

$jar = Get-ChildItem -Path (Join-Path $backendDir "target") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*sources.jar" -and $_.Name -notlike "*javadoc.jar" } |
    Select-Object -First 1

if (-not $jar) {
    throw "Backend jar was not found. Run without -SkipBuild first."
}

Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $backendDir "target\app.jar") -Force

Push-Location $frontendDir
try {
    npm run desktop
} finally {
    Pop-Location
}
