$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$generatedDir = Join-Path $projectRoot "generated"
$outputPath = Join-Path $generatedDir "processes.html"
$pidFile = Join-Path $generatedDir "social-loop.pid"
$queueFile = Join-Path $generatedDir "queue.jsonl"
$dailyLog = Join-Path $generatedDir "daily.log"
$threadsLog = Join-Path $generatedDir "threads-publish.log"

New-Item -ItemType Directory -Force -Path $generatedDir | Out-Null

function Get-PostingProcesses {
  $items = @()
  $matchedByCommandLine = $false

  try {
    $raw = Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
      ($_.CommandLine -match [regex]::Escape($projectRoot)) -or
      ($_.CommandLine -match 'src\\cli\.js') -or
      ($_.CommandLine -match 'run-daily\.ps1') -or
      ($_.CommandLine -match 'social-loop\.ps1') -or
      ($_.CommandLine -match 'publish-threads-local\.ps1')
    }

    foreach ($proc in $raw) {
      $runtime = Get-Process -Id $proc.ProcessId -ErrorAction SilentlyContinue
      $items += [pscustomobject]@{
        Id = $proc.ProcessId
        ProcessName = $proc.Name
        Role = Get-RoleFromCommandLine $proc.CommandLine
        CpuSeconds = if ($runtime -and $null -ne $runtime.CPU) { [math]::Round($runtime.CPU, 1) } else { 0 }
        MemoryMb = if ($runtime) { [math]::Round($runtime.WorkingSet64 / 1MB, 1) } else { 0 }
        Threads = if ($runtime) { $runtime.Threads.Count } else { 0 }
        StartedAt = if ($runtime) {
          try { $runtime.StartTime.ToString("yyyy-MM-dd HH:mm:ss") } catch { "" }
        } else { "" }
        MatchReason = "command line"
      }
    }

    $matchedByCommandLine = $true
  } catch {
    $matchedByCommandLine = $false
  }

  if (-not $matchedByCommandLine -and (Test-Path $pidFile)) {
    $pidValue = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1

    if ($pidValue) {
      $runtime = Get-Process -Id $pidValue -ErrorAction SilentlyContinue

      if ($runtime) {
        $startedAt = ""
        try {
          $startedAt = $runtime.StartTime.ToString("yyyy-MM-dd HH:mm:ss")
        } catch {
          $startedAt = ""
        }

        $items += [pscustomobject]@{
          Id = $runtime.Id
          ProcessName = $runtime.ProcessName
          Role = "social loop"
          CpuSeconds = if ($null -ne $runtime.CPU) { [math]::Round($runtime.CPU, 1) } else { 0 }
          MemoryMb = [math]::Round($runtime.WorkingSet64 / 1MB, 1)
          Threads = $runtime.Threads.Count
          StartedAt = $startedAt
          MatchReason = "pid file"
        }
      }
    }
  }

  return ,$items
}

function Get-RoleFromCommandLine([string]$commandLine) {
  if ($commandLine -match 'social-loop\.ps1') { return "social loop" }
  if ($commandLine -match 'run-daily\.ps1') { return "daily runner" }
  if ($commandLine -match 'publish-threads-local\.ps1') { return "threads publisher" }
  if ($commandLine -match 'src\\cli\.js') { return "node cli" }
  return "project process"
}

function Get-LineCount([string]$path) {
  if (-not (Test-Path $path)) { return 0 }
  return (Get-Content $path | Where-Object { $_.Trim() }).Count
}

function Get-LastNonEmptyLine([string]$path) {
  if (-not (Test-Path $path)) { return "" }
  $line = Get-Content $path | Where-Object { $_.Trim() } | Select-Object -Last 1
  return [string]$line
}

function Escape-Html([string]$value) {
  if ($null -eq $value) { return "" }
  return $value.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;").Replace('"', "&quot;")
}

$processes = Get-PostingProcesses | Sort-Object -Property CpuSeconds -Descending
$updatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
$queueCount = Get-LineCount $queueFile
$loopPid = if (Test-Path $pidFile) { Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1 } else { "" }
$loopRunning = $false

if ($loopPid) {
  $loopRunning = [bool](Get-Process -Id $loopPid -ErrorAction SilentlyContinue)
}

$rows = if ($processes.Count -gt 0) {
  ($processes | ForEach-Object {
    @"
<tr>
  <td>$($_.Role)</td>
  <td>$($_.Id)</td>
  <td>$($_.ProcessName)</td>
  <td data-value="$($_.CpuSeconds)">$($_.CpuSeconds)</td>
  <td data-value="$($_.MemoryMb)">$($_.MemoryMb)</td>
  <td data-value="$($_.Threads)">$($_.Threads)</td>
  <td>$($_.StartedAt)</td>
  <td>$($_.MatchReason)</td>
</tr>
"@
  }) -join "`n"
} else {
  @"
<tr>
  <td colspan="8" class="empty">No active posting process detected right now.</td>
</tr>
"@
}

$statusText = if ($loopRunning) { "running" } elseif ($loopPid) { "stopped" } else { "not started" }
$statusClass = if ($loopRunning) { "ok" } elseif ($loopPid) { "warn" } else { "idle" }
$lastDailyLine = Escape-Html (Get-LastNonEmptyLine $dailyLog)
$lastThreadsLine = Escape-Html (Get-LastNonEmptyLine $threadsLog)
$topProcess = $processes | Select-Object -First 1
$topProcessName = if ($topProcess) { $topProcess.ProcessName } else { "none" }
$topProcessRole = if ($topProcess) { $topProcess.Role } else { "waiting" }

$html = @"
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta http-equiv="refresh" content="15">
  <title>Posting Process Dashboard</title>
  <style>
    :root {
      --bg: #11161c;
      --panel: #19232d;
      --panel-2: #21303d;
      --text: #eef4f8;
      --muted: #9eb1c1;
      --accent: #67e8f9;
      --border: #34485a;
      --good: #34d399;
      --warn: #f59e0b;
      --idle: #94a3b8;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: "Segoe UI", system-ui, sans-serif;
      color: var(--text);
      background:
        radial-gradient(circle at top left, rgba(103, 232, 249, 0.16), transparent 26%),
        radial-gradient(circle at bottom right, rgba(52, 211, 153, 0.12), transparent 20%),
        var(--bg);
    }
    .wrap {
      max-width: 1280px;
      margin: 0 auto;
      padding: 32px 20px 48px;
    }
    h1 {
      margin: 0;
      font-size: 42px;
      letter-spacing: -0.03em;
    }
    .subtitle {
      margin-top: 10px;
      color: var(--muted);
      font-size: 15px;
      max-width: 780px;
    }
    .updated {
      margin-top: 14px;
      color: var(--muted);
      font-size: 14px;
    }
    .stats {
      display: grid;
      grid-template-columns: repeat(4, minmax(180px, 1fr));
      gap: 14px;
      margin: 24px 0;
    }
    .card, .log-card, .table-shell {
      background: linear-gradient(180deg, rgba(33, 48, 61, 0.95), rgba(25, 35, 45, 0.97));
      border: 1px solid var(--border);
      border-radius: 18px;
      box-shadow: 0 14px 30px rgba(0, 0, 0, 0.22);
    }
    .card {
      padding: 18px;
    }
    .label {
      color: var(--muted);
      font-size: 13px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }
    .value {
      margin-top: 8px;
      font-size: 30px;
      font-weight: 700;
    }
    .hint {
      margin-top: 8px;
      color: var(--muted);
      font-size: 13px;
    }
    .badge {
      display: inline-block;
      padding: 5px 10px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }
    .badge.ok { background: rgba(52, 211, 153, 0.14); color: var(--good); }
    .badge.warn { background: rgba(245, 158, 11, 0.14); color: var(--warn); }
    .badge.idle { background: rgba(148, 163, 184, 0.14); color: var(--idle); }
    .logs {
      display: grid;
      grid-template-columns: repeat(2, minmax(260px, 1fr));
      gap: 14px;
      margin-bottom: 18px;
    }
    .log-card {
      padding: 18px;
    }
    .log-line {
      margin-top: 10px;
      color: var(--text);
      font-size: 14px;
      line-height: 1.5;
      word-break: break-word;
    }
    .table-shell {
      overflow: hidden;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    thead {
      background: rgba(33, 48, 61, 0.98);
    }
    th, td {
      padding: 14px 16px;
      text-align: left;
      border-bottom: 1px solid rgba(52, 72, 90, 0.7);
      font-size: 14px;
    }
    th {
      color: var(--muted);
      font-weight: 600;
    }
    tbody tr:hover {
      background: rgba(103, 232, 249, 0.05);
    }
    .empty {
      color: var(--muted);
      text-align: center;
      padding: 28px 16px;
    }
    @media (max-width: 980px) {
      .stats, .logs {
        grid-template-columns: repeat(2, minmax(180px, 1fr));
      }
      .table-shell {
        overflow: auto;
      }
      table {
        min-width: 860px;
      }
    }
    @media (max-width: 640px) {
      .stats, .logs {
        grid-template-columns: 1fr;
      }
      h1 { font-size: 34px; }
    }
  </style>
</head>
<body>
  <div class="wrap">
    <h1>Posting Process Dashboard</h1>
    <div class="subtitle">Only processes related to this posting project are shown. When command-line inspection is blocked, the dashboard falls back to the social loop PID file and project logs.</div>
    <div class="updated">Updated: $updatedAt</div>

    <div class="stats">
      <div class="card">
        <div class="label">Posting Status</div>
        <div class="value"><span class="badge $statusClass">$statusText</span></div>
        <div class="hint">Based on `generated/social-loop.pid`</div>
      </div>
      <div class="card">
        <div class="label">Detected Processes</div>
        <div class="value">$($processes.Count)</div>
        <div class="hint">Filtered to posting-related runtime only</div>
      </div>
      <div class="card">
        <div class="label">Queue Items</div>
        <div class="value">$queueCount</div>
        <div class="hint">Current lines in `generated/queue.jsonl`</div>
      </div>
      <div class="card">
        <div class="label">Top Match</div>
        <div class="value">$topProcessName</div>
        <div class="hint">$topProcessRole</div>
      </div>
    </div>

    <div class="logs">
      <div class="log-card">
        <div class="label">Last Daily Log Line</div>
        <div class="log-line">$lastDailyLine</div>
      </div>
      <div class="log-card">
        <div class="label">Last Threads Log Line</div>
        <div class="log-line">$lastThreadsLine</div>
      </div>
    </div>

    <div class="table-shell">
      <table>
        <thead>
          <tr>
            <th>Role</th>
            <th>PID</th>
            <th>Process</th>
            <th>CPU sec</th>
            <th>Memory MB</th>
            <th>Threads</th>
            <th>Started</th>
            <th>Matched By</th>
          </tr>
        </thead>
        <tbody>
$rows
        </tbody>
      </table>
    </div>
  </div>
</body>
</html>
"@

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($outputPath, $html, $utf8NoBom)
Write-Host "Saved process dashboard to $outputPath"
