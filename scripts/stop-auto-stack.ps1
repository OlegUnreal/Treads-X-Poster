param(
    [switch]$UseDocker,
    [string]$ComposeFile = "docker-compose.local.yml"
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $repoRoot '.runtime-stack'
$pidPath = Join-Path $runtimeDir 'pids.txt'
$composePath = Join-Path $repoRoot $ComposeFile

if ($UseDocker) {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue) -and -not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
        throw "Docker Compose is not available."
    }
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        try {
            $null = & docker compose version
            & docker compose -f $composePath down
        } catch {
            & docker-compose -f $composePath down
        }
    } else {
        & docker-compose -f $composePath down
    }
    Write-Host "Stopped Docker stack."
    return
}

if (-not (Test-Path -LiteralPath $pidPath)) {
    Write-Host "No runtime pids file found. No local stack processes to stop." -ForegroundColor Yellow
    return
}

$pids = Get-Content -Raw -LiteralPath $pidPath | ConvertFrom-Json -ErrorAction SilentlyContinue
if (-not $pids) {
    Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
    return
}

foreach ($entry in $pids.PSObject.Properties) {
    $pid = [int]$entry.Value
    try {
        if ($pid -gt 0) {
            Stop-Process -Id $pid -Force -ErrorAction Stop
            Write-Host "Stopped process $($entry.Name): $pid"
        }
    } catch {
        Write-Host "Process $($entry.Name) ($pid) was already stopped."
    }
}

Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
Write-Host "Local stack stopped."
