param(
    [int]$Count = 1,
    [string]$Url = "profile-home",
    [string[]]$Profiles = @(),
    [int]$DelayFrom = 0,
    [int]$DelayTo = 0,
    [string]$RuntimeDir = "$env:USERPROFILE\chrome-proxy-profiles",
    [string]$ChromePath = "C:\Program Files\Google\Chrome\Application\chrome.exe",
    [switch]$SyncWebShareProxies,
    [string]$DopplerProject = "behind-the-smile",
    [string]$DopplerConfig = "prd"
)

$ErrorActionPreference = "Stop"

function Read-EnvFile {
    param([string]$Path)
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing profiles.env: $Path"
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }
        if ($trimmed -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            $key = $matches[1]
            $value = $matches[2].Trim()
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $values[$key] = $value
        }
    }
    return $values
}

function Get-PythonPath {
    foreach ($name in @("python.exe", "python3.exe", "py.exe")) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd) {
            return $cmd.Source
        }
    }
    throw "Python was not found on PATH."
}

function Get-DopplerPath {
    $machinePath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
    foreach ($pathPart in (($machinePath + ";" + $userPath) -split ";")) {
        if ($pathPart -and ($env:Path -notlike "*$pathPart*")) {
            $env:Path = $env:Path + ";" + $pathPart
        }
    }

    $cmd = Get-Command "doppler.exe" -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $wingetRoot = Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages"
    if (Test-Path -LiteralPath $wingetRoot) {
        $candidate = Get-ChildItem -Path $wingetRoot -Recurse -Filter doppler.exe -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($candidate) {
            return $candidate.FullName
        }
    }
    return $null
}

function Sync-WebShareProfiles {
    param(
        [string]$ImporterPath,
        [string]$EnvPath,
        [string]$Project,
        [string]$Config
    )

    if (-not (Test-Path -LiteralPath $ImporterPath)) {
        throw "Missing WebShare importer: $ImporterPath"
    }

    $token = $env:WEBSHARE_API_TOKEN
    if (-not $token) {
        $doppler = Get-DopplerPath
        if ($doppler) {
            $token = (& $doppler secrets get WEBSHARE_API_TOKEN --project $Project --config $Config --plain 2>$null)
            if ($LASTEXITCODE -ne 0) {
                $token = $null
            }
        }
    }

    if (-not $token) {
        throw "Missing WEBSHARE_API_TOKEN. Set it locally or log in to Doppler with access to $Project/$Config."
    }

    $python = Get-PythonPath
    $previousToken = $env:WEBSHARE_API_TOKEN
    try {
        $env:WEBSHARE_API_TOKEN = $token.Trim()
        & $python $ImporterPath --from-webshare-api --env $EnvPath
        if ($LASTEXITCODE -ne 0) {
            throw "WebShare proxy sync failed."
        }
    } finally {
        $env:WEBSHARE_API_TOKEN = $previousToken
    }
}

function Test-PortListening {
    param([int]$Port)
    try {
        return [bool](Get-NetTCPConnection -LocalAddress "127.0.0.1" -LocalPort $Port -State Listen -ErrorAction Stop)
    } catch {
        return $false
    }
}

function Stop-ListeningPort {
    param([int]$Port)
    $connections = Get-NetTCPConnection -LocalAddress "127.0.0.1" -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($connection in $connections) {
        if ($connection.OwningProcess) {
            Stop-Process -Id $connection.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    }
}

function Start-Forwarder {
    param(
        [string]$Listen,
        [string]$Upstream,
        [string]$ProfileName
    )
    if (-not $Listen.StartsWith("127.0.0.1:")) {
        return
    }
    $port = [int]($Listen.Split(":")[-1])
    if (Test-PortListening -Port $port) {
        Stop-ListeningPort -Port $port
        Start-Sleep -Milliseconds 500
    }
    $python = Get-PythonPath
    $forwarder = Join-Path $RuntimeDir "proxy-forwarder.py"
    $stdoutLog = Join-Path $RuntimeDir "$ProfileName-proxy.out.log"
    $stderrLog = Join-Path $RuntimeDir "$ProfileName-proxy.err.log"
    Start-Process `
        -FilePath $python `
        -ArgumentList @($forwarder, $Listen, $Upstream) `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -WindowStyle Hidden
    Start-Sleep -Seconds 1
    if (-not (Test-PortListening -Port $port)) {
        $errorText = ""
        if (Test-Path -LiteralPath $stderrLog) {
            $errorText = (Get-Content -LiteralPath $stderrLog -Raw).Trim()
        }
        if ($errorText) {
            throw "Proxy forwarder for $ProfileName did not start on $Listen. $errorText"
        }
        throw "Proxy forwarder for $ProfileName did not start on $Listen."
    }
}

function New-ProfileHome {
    param(
        [string]$ProfileName,
        [string]$Proxy,
        [string]$Upstream
    )
    $homePath = Join-Path $RuntimeDir "$ProfileName-home.html"
    $safeUpstream = $Upstream -replace '(https?://)[^:@/]+:[^@/]+@', '$1***:***@'
    @"
<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <title>$ProfileName</title>
  <style>
    body { margin: 0; font-family: Arial, sans-serif; background: #111827; color: #f9fafb; }
    main { padding: 44px; }
    h1 { font-size: 72px; margin: 0 0 16px; }
    .line { font-size: 28px; margin: 14px 0; }
    .muted { color: #cbd5e1; }
    a { color: #67e8f9; font-size: 24px; display: inline-block; margin-top: 24px; }
  </style>
</head>
<body>
  <main>
    <h1>$ProfileName</h1>
    <div class="line">Profile: <strong>$ProfileName</strong></div>
    <div class="line">Chrome proxy: <strong>$Proxy</strong></div>
    <div class="line muted">Upstream: $safeUpstream</div>
    <div class="line muted">Started: $(Get-Date -Format o)</div>
    <a href="https://browserleaks.com/ip">Open IP leak check</a>
  </main>
</body>
</html>
"@ | Set-Content -LiteralPath $homePath -Encoding UTF8
    return "file:///$($homePath.Replace('\', '/'))"
}

function Should-UseIncognito {
    param(
        [hashtable]$Env,
        [string]$LaunchUrl
    )
    $mode = "true"
    if ($Env.ContainsKey("INCOGNITO_MODE")) {
        $mode = $Env["INCOGNITO_MODE"]
    }
    if ($mode -eq "true") {
        return $true
    }
    $domains = ""
    if ($Env.ContainsKey("INCOGNITO_DOMAINS")) {
        $domains = $Env["INCOGNITO_DOMAINS"]
    }
    foreach ($domain in ($domains -split '\s+')) {
        if ($domain -and $LaunchUrl.Contains($domain)) {
            return $true
        }
    }
    return $false
}

if (-not (Test-Path -LiteralPath $ChromePath)) {
    throw "Chrome was not found: $ChromePath"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$forwarderSource = Join-Path $repoRoot "remote-chrome-profiles\proxy-forwarder.py"
$importerSource = Join-Path $repoRoot "remote-chrome-profiles\import-webshare-proxies.py"
$envFile = Join-Path $RuntimeDir "profiles.env"
New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $RuntimeDir "data") | Out-Null

Copy-Item -LiteralPath $forwarderSource -Destination (Join-Path $RuntimeDir "proxy-forwarder.py") -Force
if (Test-Path -LiteralPath $importerSource) {
    Copy-Item -LiteralPath $importerSource -Destination (Join-Path $RuntimeDir "import-webshare-proxies.py") -Force
}

if ($SyncWebShareProxies) {
    Sync-WebShareProfiles `
        -ImporterPath (Join-Path $RuntimeDir "import-webshare-proxies.py") `
        -EnvPath $envFile `
        -Project $DopplerProject `
        -Config $DopplerConfig
}

if (-not (Test-Path -LiteralPath $envFile)) {
    $example = Join-Path $repoRoot "remote-chrome-profiles\profiles.env.example"
    Copy-Item -LiteralPath $example -Destination $envFile
    Write-Host "Created template profiles.env at $envFile. Fill proxy values before running again."
    exit 1
}

$profileEnv = Read-EnvFile -Path $envFile
$profileNames = @()
if ($Profiles.Count -gt 0) {
    $profileNames = $Profiles
} else {
    $profileNamesValue = ""
    if ($profileEnv.ContainsKey("PROFILE_NAMES")) {
        $profileNamesValue = $profileEnv["PROFILE_NAMES"]
    }
    $profileNames = ($profileNamesValue -split '\s+' | Where-Object { $_ })
}

if ($profileNames.Count -eq 0) {
    throw "No profiles found in PROFILE_NAMES."
}

$Count = [Math]::Max(1, [Math]::Min($Count, $profileNames.Count))
$selectedProfiles = $profileNames | Select-Object -First $Count

$DelayFrom = [Math]::Max(0, $DelayFrom)
$DelayTo = [Math]::Max($DelayFrom, $DelayTo)
$windowSize = "1000,760"
if ($profileEnv.ContainsKey("WINDOW_SIZE")) {
    $windowSize = $profileEnv["WINDOW_SIZE"]
}

$started = 0
foreach ($profileName in $selectedProfiles) {
    $proxy = $profileEnv["PROXY_$profileName"]
    $upstream = $profileEnv["UPSTREAM_PROXY_$profileName"]
    if (-not $proxy) {
        throw "Missing PROXY_$profileName in $envFile"
    }
    if ($proxy.StartsWith("http://127.0.0.1:") -and $upstream) {
        Start-Forwarder -Listen ($proxy -replace '^http://', '') -Upstream $upstream -ProfileName $profileName
    }

    $profileDir = Join-Path $RuntimeDir "data\$profileName"
    New-Item -ItemType Directory -Force -Path $profileDir | Out-Null

    $launchUrl = $Url
    if ($launchUrl -eq "profile-home") {
        $launchUrl = New-ProfileHome -ProfileName $profileName -Proxy $proxy -Upstream $upstream
    }

    $position = $profileEnv["WINDOW_POSITION_$profileName"]
    $chromeArgs = @(
        "--user-data-dir=$profileDir",
        "--proxy-server=$proxy",
        "--no-sandbox",
        "--disable-dev-shm-usage",
        "--disable-background-networking",
        "--disable-background-timer-throttling",
        "--disable-renderer-backgrounding",
        "--disable-extensions",
        "--disable-sync",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-features=Translate,OptimizationHints,MediaRouter,WebRtcHideLocalIpsWithMdns",
        "--autoplay-policy=no-user-gesture-required",
        "--force-webrtc-ip-handling-policy=disable_non_proxied_udp",
        "--window-size=$windowSize",
        "--new-window",
        $launchUrl
    )
    if ($position) {
        $chromeArgs = @("--window-position=$position") + $chromeArgs
    }
    if (Should-UseIncognito -Env $profileEnv -LaunchUrl $launchUrl) {
        $chromeArgs = @("--incognito") + $chromeArgs
    }

    Start-Process -FilePath $ChromePath -ArgumentList $chromeArgs
    $started++
    Write-Host "Started $profileName through $proxy"

    if ($started -lt $selectedProfiles.Count -and $DelayTo -gt 0) {
        $delay = if ($DelayTo -gt $DelayFrom) { Get-Random -Minimum $DelayFrom -Maximum ($DelayTo + 1) } else { $DelayFrom }
        Start-Sleep -Seconds $delay
    }
}

Write-Host "Started $started local profile(s)."
