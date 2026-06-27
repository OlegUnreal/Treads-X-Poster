$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$generatedDir = Join-Path $projectRoot "generated"
$pidFile = Join-Path $generatedDir "social-loop.pid"
$loopScript = Join-Path $projectRoot "scripts\social-loop.ps1"
$intervalHours = if ($args.Count -gt 0) { $args[0] } else { "4" }
$threadsPerRun = if ($args.Count -gt 1) { $args[1] } else { "1" }
$xPerRun = if ($args.Count -gt 2) { $args[2] } else { "1" }
$minimumReady = if ($args.Count -gt 3) { $args[3] } else { "8" }

New-Item -ItemType Directory -Force -Path $generatedDir | Out-Null

if (Test-Path $pidFile) {
  $existingPid = Get-Content $pidFile -ErrorAction SilentlyContinue

  if ($existingPid) {
    $process = Get-Process -Id $existingPid -ErrorAction SilentlyContinue

    if ($process) {
      throw "Social loop is already running with PID $existingPid."
    }
  }
}

$process = Start-Process `
  -FilePath "powershell.exe" `
  -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$loopScript`" $intervalHours $threadsPerRun $xPerRun $minimumReady" `
  -WorkingDirectory $projectRoot `
  -WindowStyle Hidden `
  -PassThru

Set-Content -Path $pidFile -Value $process.Id -Encoding ascii
Write-Host "Started social loop with PID $($process.Id)."
