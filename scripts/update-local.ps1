param(
    [switch]$BuildExe,
    [switch]$SkipInstall,
    [switch]$AllowDirty
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot "frontend"
$agentDir = Join-Path $repoRoot "windows-agent"
$chromeRuntimeDir = Join-Path $env:USERPROFILE "chrome-proxy-profiles"

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    Write-Host ""
    Write-Host "==> $Name"
    & $Action
}

function Assert-CleanGitState {
    $status = git -C $repoRoot status --porcelain
    $blocking = @()
    foreach ($line in $status) {
        if ($line -match '^\?\? (frontend/\.idea/|tools/)') {
            continue
        }
        $blocking += $line
    }

    if ($blocking.Count -gt 0 -and -not $AllowDirty) {
        Write-Host "Local git changes were found:" -ForegroundColor Yellow
        $blocking | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
        throw "Commit/stash local changes first, or rerun with -AllowDirty if you intentionally want to update over them."
    }
}

Push-Location $repoRoot
try {
    Invoke-Step "Checking local Chrome profile runtime" {
        if (Test-Path -LiteralPath $chromeRuntimeDir) {
            Write-Host "Leaving Chrome profile runtime untouched: $chromeRuntimeDir"
        } else {
            Write-Host "Chrome profile runtime does not exist yet: $chromeRuntimeDir"
        }
    }

    Invoke-Step "Checking git state" {
        Assert-CleanGitState
    }

    Invoke-Step "Pulling latest code" {
        git pull --ff-only
    }

    Invoke-Step "Building Windows agent" {
        mvn -f (Join-Path $agentDir "pom.xml") package -DskipTests
    }

    Invoke-Step "Building frontend" {
        Push-Location $frontendDir
        try {
            if (-not $SkipInstall) {
                if (Test-Path -LiteralPath "package-lock.json") {
                    npm install
                } else {
                    npm install --no-package-lock
                }
            }
            npm run build
        } finally {
            Pop-Location
        }
    }

    Invoke-Step "Updating Windows agent app.jar" {
        $jar = Get-ChildItem -Path (Join-Path $agentDir "target") -Filter "*.jar" |
            Where-Object { $_.Name -ne "app.jar" -and $_.Name -notlike "*.original" -and $_.Name -notlike "*sources.jar" -and $_.Name -notlike "*javadoc.jar" } |
            Select-Object -First 1
        if (-not $jar) {
            throw "Windows agent jar was not found."
        }
        Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $agentDir "target\app.jar") -Force
        Write-Host "Updated windows-agent\target\app.jar"
    }

    if ($BuildExe) {
        Invoke-Step "Packaging Windows app" {
            $args = @()
            if ($SkipInstall) {
                $args += "-SkipInstall"
            }
            & (Join-Path $PSScriptRoot "package-windows-app.ps1") @args
        }
    }

    Write-Host ""
    Write-Host "Update complete." -ForegroundColor Green
    Write-Host "Open Chrome tabs and profile data under $chromeRuntimeDir were not modified."
    if (-not $BuildExe) {
        Write-Host "Run with -BuildExe when you need to rebuild frontend\release\BehindTheSmilePlayback.exe."
    }
} finally {
    Pop-Location
}
