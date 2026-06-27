$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$bundledNode = "C:\Users\ZEPHYRUS\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
$node = if (Test-Path $bundledNode) { $bundledNode } else { "node" }
$logDir = Join-Path $projectRoot "generated"
$logFile = Join-Path $logDir "auto-create.log"

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

& $node (Join-Path $projectRoot "legacy-node\cli.js") auto-create --plan (Join-Path $projectRoot "backend\config\content-plan.json") *>> $logFile
