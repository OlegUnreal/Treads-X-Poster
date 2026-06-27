$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$bundledNode = "C:\Users\ZEPHYRUS\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
$node = if (Test-Path $bundledNode) { $bundledNode } else { "node" }
$logDir = Join-Path $projectRoot "generated"
$logFile = Join-Path $logDir "daily.log"
$threadsPerRun = if ($args.Count -gt 0) { $args[0] } else { "1" }
$xPerRun = if ($args.Count -gt 1) { $args[1] } else { "1" }
$minimumReady = if ($args.Count -gt 2) { $args[2] } else { "6" }

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

& $node (Join-Path $projectRoot "legacy-node\cli.js") daily --plan (Join-Path $projectRoot "backend\config\content-plan.json") --threads-per-run $threadsPerRun --x-per-run $xPerRun --minimum-ready $minimumReady *>> $logFile
