$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$bundledNode = "C:\Users\ZEPHYRUS\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
$node = if (Test-Path $bundledNode) { $bundledNode } else { "node" }
$logDir = Join-Path $projectRoot "generated"
$logFile = Join-Path $logDir "threads-publish.log"
$index = if ($args.Count -gt 0) { $args[0] } else { "1" }

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

try {
  $connectivity = Test-NetConnection -ComputerName "graph.threads.net" -Port 443 -WarningAction SilentlyContinue

  if (-not $connectivity.TcpTestSucceeded) {
    throw "Cannot reach graph.threads.net:443 from this machine. Check firewall, proxy, VPN, or antivirus HTTPS filtering."
  }
} catch {
  $_ | Out-String | Tee-Object -FilePath $logFile -Append | Out-Host
  exit 1
}

& $node (Join-Path $projectRoot "legacy-node\cli.js") publish-queued-threads --index $index *>> $logFile
