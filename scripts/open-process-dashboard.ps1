$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$renderScript = Join-Path $projectRoot "scripts\render-process-dashboard.ps1"
$dashboardPath = Join-Path $projectRoot "generated\processes.html"

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $renderScript
Start-Process $dashboardPath
