$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$generatedDir = Join-Path $projectRoot "generated"
$pidFile = Join-Path $generatedDir "social-loop.pid"

if (-not (Test-Path $pidFile)) {
  throw "Social loop PID file not found."
}

$pidValue = Get-Content $pidFile -ErrorAction Stop
$process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue

if ($process) {
  Stop-Process -Id $pidValue -Force
}

Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
Write-Host "Stopped social loop PID $pidValue."
