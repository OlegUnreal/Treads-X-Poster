$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$scriptPath = Join-Path $projectRoot "scripts\run-daily.ps1"
$taskName = "Behind The Smile Frequent"
$startTime = if ($args.Count -gt 0) { $args[0] } else { "09:00" }
$intervalHours = if ($args.Count -gt 1) { [int]$args[1] } else { 4 }
$threadsPerRun = if ($args.Count -gt 2) { $args[2] } else { "1" }
$xPerRun = if ($args.Count -gt 3) { $args[3] } else { "1" }
$minimumReady = if ($args.Count -gt 4) { $args[4] } else { "8" }

$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`" $threadsPerRun $xPerRun $minimumReady"
$trigger = New-ScheduledTaskTrigger -Once -At $startTime -RepetitionInterval (New-TimeSpan -Hours $intervalHours) -RepetitionDuration (New-TimeSpan -Days 3650)
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description "Creates social posts and publishes to Threads and X several times per day." -Force

Write-Host "Installed scheduled task '$taskName': every $intervalHours hour(s), starting at $startTime, $threadsPerRun Threads post(s) and $xPerRun X post(s) per run."
