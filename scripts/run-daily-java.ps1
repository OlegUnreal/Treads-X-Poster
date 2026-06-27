$ErrorActionPreference = "Stop"

$threadsPerRun = if ($args.Count -gt 0) { $args[0] } else { "1" }
$xPerRun = if ($args.Count -gt 1) { $args[1] } else { "1" }
$minimumReady = if ($args.Count -gt 2) { $args[2] } else { "8" }
$arguments = "daily --threads-per-run $threadsPerRun --x-per-run $xPerRun --minimum-ready $minimumReady"

mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=$arguments"
