param(
    [switch]$UseDocker,
    [switch]$Rebuild,
    [switch]$SkipBuild,
    [switch]$NoBrowser,
    [string]$ComposeFile = "docker-compose.local.yml",
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 4200
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot 'backend'
$frontendDir = Join-Path $repoRoot 'frontend'
$composePath = Join-Path $repoRoot $ComposeFile
$runtimeDir = Join-Path $repoRoot '.runtime-stack'
$pidPath = Join-Path $runtimeDir 'pids.txt'
$backendEnvFile = Join-Path $backendDir 'config\.env'
$backendEnvExample = Join-Path $backendDir 'config\.env.example'

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Name. Add it to PATH."
    }
}

function Resolve-Java {
    $java = "$env:JAVA_HOME\bin\java.exe"
    if (Test-Path -LiteralPath $java) {
        return $java
    }
    $fallback = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $fallback) {
        throw "Java executable not found. Install Java 21 and set JAVA_HOME."
    }
    return $fallback.Source
}

function Get-ComposeCommand {
    if (Get-Command 'docker' -ErrorAction SilentlyContinue) {
        try {
            $null = & docker compose version
            return @('docker','compose')
        } catch {
            try {
                $null = & docker-compose version
                return @('docker-compose')
            } catch {
                return $null
            }
        }
    }
    return $null
}

function Wait-HttpReady {
    param(
        [string]$Url,
        [string]$Description = "Service"
    )

    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            Invoke-RestMethod -Uri $Url -TimeoutSec 3 | Out-Null
            Write-Host "$Description is ready." -ForegroundColor Green
            return
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "$Description failed to start. Check logs in $runtimeDir."
}

function Stop-StaleProcesses {
    param([hashtable]$KnownPids)
    foreach ($item in $KnownPids.Keys) {
        $pid = $KnownPids[$item]
        if ($pid -gt 0) {
            Stop-Process -Id $pid -ErrorAction SilentlyContinue
        }
    }
}

if (-not (Test-Path -LiteralPath $backendEnvFile) -and (Test-Path -LiteralPath $backendEnvExample)) {
    Write-Host "No backend .env found. Creating from example." -ForegroundColor Yellow
    Copy-Item -LiteralPath $backendEnvExample -Destination $backendEnvFile
}

if ($UseDocker) {
    $compose = Get-ComposeCommand
    if ($null -eq $compose) {
        throw "Docker Compose is not available. Install Docker Desktop or docker-compose."
    }
    if (-not (Test-Path -LiteralPath $composePath)) {
        throw "Compose file not found: $composePath"
    }

    $composeArgs = @('compose', '-f', $composePath)
    if ($compose[0] -eq 'docker-compose') {
        $composeArgs = @('-f', $composePath)
    }

    if ($Rebuild) {
        & $compose[0] $composeArgs @('build', '--pull', '--no-cache')
    } else {
        & $compose[0] $composeArgs @('build', '--pull')
    }

    & $compose[0] $composeArgs @('up', '-d')
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed."
    }

    Wait-HttpReady -Url "http://127.0.0.1:$BackendPort/api/health" -Description "Backend"
    Write-Host "Backend + Frontend are running:"
    Write-Host "  Backend:  http://127.0.0.1:$BackendPort"
    Write-Host "  Frontend: http://127.0.0.1:$FrontendPort"
    if (-not $NoBrowser) {
        Start-Process "http://127.0.0.1:$FrontendPort" -ErrorAction SilentlyContinue
    }
    Write-Host "Stop with: .\scripts\stop-auto-stack.ps1 -UseDocker" -ForegroundColor Cyan
    return
}

if ($Rebuild) {
    $SkipBuild = $false
}

if (-not (Test-Path -LiteralPath (Join-Path $backendDir 'target\social-posting-0.1.0.jar')) -or -not $SkipBuild) {
    Require-Command mvn
    Write-Host "Building backend jar..." -ForegroundColor Cyan
    Push-Location $backendDir
    try {
        mvn -f "pom.xml" package -DskipTests
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath (Join-Path $backendDir 'target\social-posting-0.1.0.jar'))) {
    throw "Backend jar is still missing. Build step failed."
}

Require-Command node
Require-Command npm

$npmCommand = (Get-Command npm).Source
$javaCommand = Resolve-Java

Push-Location $frontendDir
try {
    if (-not (Test-Path -LiteralPath "node_modules")) {
        Write-Host "Installing frontend dependencies..." -ForegroundColor Cyan
        npm install
    }
} finally {
    Pop-Location
}

if (Test-Path -LiteralPath $pidPath) {
    Remove-Item -LiteralPath $pidPath -Force
}

Stop-StaleProcesses @{
    Backend = 0
    Frontend = 0
}

Push-Location $backendDir
try {
    Write-Host "Starting backend..." -ForegroundColor Cyan
    $backendProc = Start-Process -FilePath $javaCommand -ArgumentList @(
        '-jar',
        (Join-Path $backendDir 'target\social-posting-0.1.0.jar')
    ) -PassThru -NoNewWindow
} finally {
    Pop-Location
}

Push-Location $frontendDir
try {
    Write-Host "Starting frontend..." -ForegroundColor Cyan
    $frontendProc = Start-Process -FilePath $npmCommand -ArgumentList @('run','start','--','--host','0.0.0.0','--port',$FrontendPort) -PassThru -NoNewWindow
} finally {
    Pop-Location
}

[pscustomobject]@{
    Backend = $backendProc.Id
    Frontend = $frontendProc.Id
} | ConvertTo-Json | Set-Content -LiteralPath $pidPath

Wait-HttpReady -Url "http://127.0.0.1:$BackendPort/api/health" -Description "Backend"
Wait-HttpReady -Url "http://127.0.0.1:$FrontendPort" -Description "Frontend"

Write-Host "Local stack is running:"
Write-Host "  Backend:  http://127.0.0.1:$BackendPort/api/health"
Write-Host "  Frontend: http://127.0.0.1:$FrontendPort"
Write-Host "Stop with: .\scripts\stop-auto-stack.ps1" -ForegroundColor Cyan

if (-not $NoBrowser) {
    Start-Process "http://127.0.0.1:$FrontendPort" -ErrorAction SilentlyContinue
}
