param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot "frontend"
$agentDir = Join-Path $repoRoot "windows-agent"

if (-not $SkipBuild) {
    mvn -f (Join-Path $agentDir "pom.xml") package -DskipTests

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

$jar = Get-ChildItem -Path (Join-Path $agentDir "target") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*sources.jar" -and $_.Name -notlike "*javadoc.jar" } |
    Select-Object -First 1

if (-not $jar) {
    throw "Windows agent jar was not found. Run without -SkipBuild first."
}

Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $agentDir "target\app.jar") -Force

Push-Location $frontendDir
try {
    npm run desktop
} finally {
    Pop-Location
}
