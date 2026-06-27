$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$runDailyScript = Join-Path $projectRoot "scripts\run-daily.ps1"
$generatedDir = Join-Path $projectRoot "generated"
$pidFile = Join-Path $generatedDir "social-loop.pid"
$heartbeatFile = Join-Path $generatedDir "social-loop-heartbeat.txt"
$intervalHours = if ($args.Count -gt 0) { [int]$args[0] } else { 4 }
$threadsPerRun = if ($args.Count -gt 1) { $args[1] } else { "1" }
$xPerRun = if ($args.Count -gt 2) { $args[2] } else { "1" }
$minimumReady = if ($args.Count -gt 3) { $args[3] } else { "8" }

New-Item -ItemType Directory -Force -Path $generatedDir | Out-Null
Set-Content -Path $pidFile -Value $PID -Encoding ascii

while ($true) {
  Set-Content -Path $heartbeatFile -Value (Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz") -Encoding ascii
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runDailyScript $threadsPerRun $xPerRun $minimumReady
  Start-Sleep -Seconds ($intervalHours * 3600)
}
