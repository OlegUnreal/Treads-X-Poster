param(
    [string]$InstallDir = "$HOME\Documents\BTS-Twitter-Treads",
    [string]$RepoUrl = "https://github.com/OlegUnreal/Treads-X-Poster.git",
    [string]$Branch = "windows",
    [switch]$BuildExe,
    [switch]$SkipInstall,
    [string]$ProfilesEnvSource = "",
    [string]$ProfilesEnvUrl = "",
    [string]$ProfilesEnvToken = ""
)

$ErrorActionPreference = "Stop"

function Require-Command {
    param(
        [string]$Name,
        [string]$Hint
    )
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing '$Name'. $Hint"
    }
}

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    Write-Host ""
    Write-Host "==> $Name"
    & $Action
}

function Sync-ProfilesEnv {
    param([string]$RuntimeDir)

    New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null
    $target = Join-Path $RuntimeDir "profiles.env"
    $capabilitiesTarget = Join-Path $RuntimeDir "proxy-capabilities.tsv"
    $capabilitiesSource = Join-Path $InstallDir "remote-chrome-profiles\proxy-capabilities.tsv"
    if ((-not (Test-Path -LiteralPath $capabilitiesTarget)) -and (Test-Path -LiteralPath $capabilitiesSource)) {
        Copy-Item -LiteralPath $capabilitiesSource -Destination $capabilitiesTarget -Force
        Write-Host "Seeded proxy-capabilities.tsv"
    }

    if ($ProfilesEnvSource) {
        if (-not (Test-Path -LiteralPath $ProfilesEnvSource)) {
            throw "ProfilesEnvSource was not found: $ProfilesEnvSource"
        }
        Copy-Item -LiteralPath $ProfilesEnvSource -Destination $target -Force
        Write-Host "Copied profiles.env from $ProfilesEnvSource"
        return
    }

    if ($ProfilesEnvUrl) {
        $headers = @{}
        if ($ProfilesEnvToken) {
            $headers["X-Profiles-Env-Token"] = $ProfilesEnvToken
        }
        Invoke-WebRequest -UseBasicParsing -Uri $ProfilesEnvUrl -Headers $headers -OutFile $target
        Write-Host "Downloaded profiles.env from $ProfilesEnvUrl"
        return
    }

    if (Test-Path -LiteralPath $target) {
        Write-Host "Keeping existing profiles.env: $target"
    } else {
        Write-Host "profiles.env is not configured yet: $target" -ForegroundColor Yellow
        Write-Host "Copy it there manually or rerun with -ProfilesEnvSource / -ProfilesEnvUrl." -ForegroundColor Yellow
    }
}

Invoke-Step "Checking prerequisites" {
    Require-Command git "Install Git for Windows: https://git-scm.com/download/win"
    Require-Command mvn "Install Maven and ensure mvn is on PATH."
    Require-Command node "Install Node.js LTS and ensure node is on PATH."
    Require-Command npm "Install Node.js LTS and ensure npm is on PATH."
}

$installParent = Split-Path -Parent $InstallDir
New-Item -ItemType Directory -Force -Path $installParent | Out-Null

if (Test-Path -LiteralPath (Join-Path $InstallDir ".git")) {
    Invoke-Step "Updating existing repository" {
        git -C $InstallDir fetch origin $Branch
        git -C $InstallDir switch $Branch
        git -C $InstallDir pull --ff-only origin $Branch
    }
} elseif (Test-Path -LiteralPath $InstallDir) {
    throw "InstallDir exists but is not a git repository: $InstallDir"
} else {
    Invoke-Step "Cloning repository" {
        git clone --branch $Branch $RepoUrl $InstallDir
    }
}

$runtimeDir = Join-Path $HOME "chrome-proxy-profiles"
Invoke-Step "Preparing local Chrome profile runtime" {
    Sync-ProfilesEnv -RuntimeDir $runtimeDir
}

Invoke-Step "Running local update/build" {
    $updateScript = Join-Path $InstallDir "scripts\update-local.ps1"
    $args = @("-AllowDirty")
    if ($BuildExe) {
        $args += "-BuildExe"
    }
    if ($SkipInstall) {
        $args += "-SkipInstall"
    }
    & $updateScript @args
}

Write-Host ""
Write-Host "Install/update complete." -ForegroundColor Green
Write-Host "Repository: $InstallDir"
Write-Host "Chrome profile runtime: $runtimeDir"
Write-Host ""
Write-Host "Start local profiles:"
Write-Host "powershell -ExecutionPolicy Bypass -File `"$InstallDir\scripts\start-local-chrome-profiles.ps1`" -Count 1 -Url profile-home"
