$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot 'backend'
$frontendDir = Join-Path $repoRoot 'frontend'
$jarPath = Join-Path $backendDir 'target\social-posting-0.1.0.jar'

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Backend jar not found: $jarPath. Build it first: mvn -f backend/pom.xml package -DskipTests"
}

$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) {
    $java = 'C:\Users\ZEPHYRUS\.jdks\openjdk-26.0.1\bin\java.exe'
}
if (-not (Test-Path -LiteralPath $java)) {
    throw "Java executable not found. Set JAVA_HOME or install Java 21+ and set JAVA_HOME."
}

$npm = Join-Path $env:APPDATA 'npm\npm.cmd'
if (-not (Test-Path -LiteralPath $npm)) {
    $npm = 'C:\Progra~1\nodejs\npm.cmd'
}
if (-not (Test-Path -LiteralPath $npm)) {
    throw "npm.cmd not found. Install Node.js and ensure npm is on PATH."
}

$backendProc = Start-Process -FilePath $java -ArgumentList @('-jar', $jarPath) -WorkingDirectory $repoRoot -PassThru
$frontendCommand = """$npm"" run start"
$frontendProc = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/c', $frontendCommand) -WorkingDirectory $frontendDir -PassThru

Write-Host "Backend PID: $($backendProc.Id)" -ForegroundColor Cyan
Write-Host "Frontend PID: $($frontendProc.Id)" -ForegroundColor Cyan
Write-Host "Backend process started." -ForegroundColor DarkGray
Write-Host "Frontend process started." -ForegroundColor DarkGray
Write-Host "`nWaiting 8 seconds for startup..." -ForegroundColor DarkGray
Start-Sleep -Seconds 8

try {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/health' -TimeoutSec 5
    Write-Host "Backend health: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "Backend health check failed: $($_.Exception.Message)" -ForegroundColor Yellow
}

try {
    $accounts = Invoke-RestMethod -Uri 'http://127.0.0.1:4200/api/accounts/config' -TimeoutSec 5
    Write-Host "Frontend proxy call OK (accounts count: $($accounts.Count))." -ForegroundColor Green
} catch {
    Write-Host "Frontend/API proxy check failed: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "`nIf everything is green, open http://127.0.0.1:4200 in your browser." -ForegroundColor Cyan
