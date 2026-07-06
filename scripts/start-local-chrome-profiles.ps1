param(
    [int]$Count = 1,
    [string]$Url = "profile-home",
    [string[]]$Profiles = @(),
    [int]$DelayFrom = 0,
    [int]$DelayTo = 0,
    [string]$RuntimeDir = "$env:USERPROFILE\chrome-proxy-profiles",
    [string]$ChromePath = "C:\Program Files\Google\Chrome\Application\chrome.exe",
    [string]$Referer = "",
    [ValidateSet("auto", "tiny", "small", "medium", "large", "hd720", "hd1080", "hd1440", "highres")]
    [string]$VideoQuality = "auto",
    [switch]$SyncWebShareProxies,
    [switch]$SkipWebShareSync,
    [string]$Mode = "open",
    [string]$DopplerProject = "behind-the-smile",
    [string]$DopplerConfig = "prd"
)

$ErrorActionPreference = "Stop"

if (-not $Mode) {
    $Mode = "open"
}
$Mode = $Mode.Trim().ToLowerInvariant()
if ($Mode -eq "play" -or $Mode -notin @("open", "login")) {
    $Mode = "open"
}

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

function Get-ProfileSetting {
    param(
        [hashtable]$EnvValues,
        [string]$ProfileName,
        [string]$Key,
        [string]$DefaultValue = ""
    )
    $profileKey = "${Key}_${ProfileName}"
    if ($EnvValues.ContainsKey($profileKey) -and $EnvValues[$profileKey]) {
        return $EnvValues[$profileKey]
    }
    if ($EnvValues.ContainsKey($Key) -and $EnvValues[$Key]) {
        return $EnvValues[$Key]
    }
    return $DefaultValue
}

function Get-PythonPath {
    if ($env:APP_PYTHON_EXE -and (Test-Path -LiteralPath $env:APP_PYTHON_EXE)) {
        $versionOutput = & $env:APP_PYTHON_EXE --version 2>&1
        if ($LASTEXITCODE -eq 0 -and ($versionOutput -join " ") -match "Python\s+3\.") {
            return $env:APP_PYTHON_EXE
        }
    }

    $machinePath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
    foreach ($pathPart in (($machinePath + ";" + $userPath) -split ";")) {
        if ($pathPart -and ($env:Path -notlike "*$pathPart*")) {
            $env:Path = $env:Path + ";" + $pathPart
        }
    }

    foreach ($name in @("py.exe", "python.exe", "python3.exe")) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if (-not $cmd) {
            continue
        }
        if ($cmd.Source -like "*\Microsoft\WindowsApps\python*.exe") {
            continue
        }

        $versionOutput = & $cmd.Source --version 2>&1
        if ($LASTEXITCODE -eq 0 -and ($versionOutput -join " ") -match "Python\s+3\.") {
            return $cmd.Source
        }
    }
    throw "Python 3 was not found. Install Python 3 from python.org or run: winget install --id Python.Python.3.12 --source winget"
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
    if (Test-IsYouTubeUrl -Value $LaunchUrl) {
        return $false
    }
    $mode = "false"
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

function Test-IsYouTubeUrl {
    param([string]$Value)
    try {
        $uri = [Uri]$Value
        $host = $uri.Host.ToLowerInvariant()
        return ($host -eq "youtube.com" -or $host.EndsWith(".youtube.com") -or $host -eq "youtu.be" -or $host.EndsWith(".youtu.be"))
    } catch {
        return $false
    }
}

function Add-YouTubeQualityParams {
    param(
        [string]$Value,
        [string]$Quality
    )
    if ($Quality -eq "auto" -or -not (Test-IsYouTubeUrl -Value $Value)) {
        return $Value
    }
    try {
        $builder = [System.UriBuilder]::new($Value)
        $params = @{}
        $queryText = $builder.Query.TrimStart("?")
        if ($queryText) {
            foreach ($part in ($queryText -split "&")) {
                if (-not $part) {
                    continue
                }
                $separator = $part.IndexOf("=")
                if ($separator -lt 0) {
                    $params[[Uri]::UnescapeDataString($part)] = ""
                } else {
                    $key = [Uri]::UnescapeDataString($part.Substring(0, $separator))
                    $valuePart = $part.Substring($separator + 1)
                    $params[$key] = [Uri]::UnescapeDataString($valuePart)
                }
            }
        }
        $params["vq"] = $Quality
        if ($Quality -ne "tiny" -and $Quality -ne "small") {
            $params["hd"] = "1"
        }
        $encoded = @()
        foreach ($key in $params.Keys) {
            $encodedKey = [Uri]::EscapeDataString([string]$key)
            $encodedValue = [Uri]::EscapeDataString([string]$params[$key])
            $encoded += "$encodedKey=$encodedValue"
        }
        $builder.Query = ($encoded -join "&")
        return $builder.Uri.AbsoluteUri
    } catch {
        return $Value
    }
}

function New-PlaybackExtension {
    param(
        [string]$ExtensionDir,
        [string]$RefererValue,
        [string]$Quality,
        [string]$AcceptLanguage
    )
    if (-not $RefererValue -and $Quality -eq "auto" -and -not $AcceptLanguage) {
        return ""
    }

    New-Item -ItemType Directory -Force -Path $ExtensionDir | Out-Null
    $manifest = [ordered]@{
        manifest_version = 3
        name = "Behind The Smile Playback Controls"
        version = "1.0.0"
        host_permissions = @("<all_urls>")
    }
    $permissions = @()
    $rules = @()
    if ($RefererValue -or $AcceptLanguage) {
        $permissions += "declarativeNetRequest"
        $manifest.declarative_net_request = @{
            rule_resources = @(@{
                id = "referer_rules"
                enabled = $true
                path = "rules.json"
            })
        }
        $ruleId = 1
        if ($RefererValue) {
            $rules += @{
                id = 1
                priority = 1
                action = @{
                    type = "modifyHeaders"
                    requestHeaders = @(@{
                        header = "Referer"
                        operation = "set"
                        value = $RefererValue
                    })
                }
                condition = @{
                    urlFilter = "|http"
                    resourceTypes = @("main_frame", "sub_frame", "xmlhttprequest", "media")
                }
            }
            $ruleId++
        }
        if ($AcceptLanguage) {
            $rules += @{
                id = $ruleId
                priority = 1
                action = @{
                    type = "modifyHeaders"
                    requestHeaders = @(@{
                        header = "Accept-Language"
                        operation = "set"
                        value = $AcceptLanguage
                    })
                }
                condition = @{
                    urlFilter = "|http"
                    resourceTypes = @("main_frame", "sub_frame", "xmlhttprequest", "media")
                }
            }
        }
        ConvertTo-Json -InputObject @($rules) -Depth 12 | Set-Content -LiteralPath (Join-Path $ExtensionDir "rules.json") -Encoding UTF8
    }
    if ($Quality -ne "auto") {
        $manifest.content_scripts = @(@{
            matches = @("*://*.youtube.com/*", "*://youtube.com/*")
            js = @("youtube-quality.js")
            run_at = "document_idle"
        })
        $qualityJson = $Quality | ConvertTo-Json -Compress
        @"
(() => {
  const quality = $qualityJson;
  let attempts = 0;
  const applyQuality = () => {
    attempts += 1;
    const player = document.getElementById('movie_player');
    try {
      if (player && typeof player.setPlaybackQualityRange === 'function') {
        player.setPlaybackQualityRange(quality);
      }
      if (player && typeof player.setPlaybackQuality === 'function') {
        player.setPlaybackQuality(quality);
      }
      localStorage.setItem('yt-player-quality', quality);
    } catch (_) {
      // YouTube can change these internals; retry quietly.
    }
    if (attempts < 30) {
      setTimeout(applyQuality, 1500);
    }
  };
  applyQuality();
})();
"@ | Set-Content -LiteralPath (Join-Path $ExtensionDir "youtube-quality.js") -Encoding UTF8
    }
    if ($permissions.Count -gt 0) {
        $manifest.permissions = $permissions
    }
    $manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $ExtensionDir "manifest.json") -Encoding UTF8
    return $ExtensionDir
}

function Write-ProfileState {
    param(
        [string]$ProfileName,
        [string]$LaunchUrl,
        [string]$ProfileDir,
        [int]$ProcessId,
        [int]$DebugPort,
        [string]$Mode,
        [string]$RefererValue,
        [string]$Quality,
        [string]$Language,
        [string]$AcceptLanguage,
        [string]$Timezone,
        [string]$UserAgent,
        [string]$WindowSize
    )
    $stateDir = Join-Path $RuntimeDir "state"
    New-Item -ItemType Directory -Force -Path $stateDir | Out-Null
    $statePath = Join-Path $stateDir "$ProfileName.env"
    $escapedUrl = $LaunchUrl.Replace('\', '\\').Replace('"', '\"')
    $escapedProfileDir = $ProfileDir.Replace('\', '\\').Replace('"', '\"')
    @"
PID="$ProcessId"
DEBUG_PORT="$DebugPort"
LAST_URL="$escapedUrl"
LAST_OPENED_AT="$(Get-Date -Format o)"
PROFILE_DIR="$escapedProfileDir"
MODE="$Mode"
REFERER="$($RefererValue.Replace('\', '\\').Replace('"', '\"'))"
VIDEO_QUALITY="$Quality"
LANGUAGE="$($Language.Replace('\', '\\').Replace('"', '\"'))"
ACCEPT_LANGUAGE="$($AcceptLanguage.Replace('\', '\\').Replace('"', '\"'))"
TIMEZONE="$($Timezone.Replace('\', '\\').Replace('"', '\"'))"
USER_AGENT="$($UserAgent.Replace('\', '\\').Replace('"', '\"'))"
WINDOW_SIZE="$($WindowSize.Replace('\', '\\').Replace('"', '\"'))"
"@ | Set-Content -LiteralPath $statePath -Encoding UTF8
}

function Get-ProfileDebugPort {
    param(
        [string]$ProfileName,
        [int]$FallbackIndex
    )
    if ($ProfileName -match '(\d+)$') {
        return 12000 + [int]$Matches[1]
    }
    return 12000 + $FallbackIndex
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
New-Item -ItemType Directory -Force -Path (Join-Path $RuntimeDir "state") | Out-Null

Copy-Item -LiteralPath $forwarderSource -Destination (Join-Path $RuntimeDir "proxy-forwarder.py") -Force
if (Test-Path -LiteralPath $importerSource) {
    Copy-Item -LiteralPath $importerSource -Destination (Join-Path $RuntimeDir "import-webshare-proxies.py") -Force
}

$shouldSyncWebShare = -not $SkipWebShareSync
if ($shouldSyncWebShare) {
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
    $profileNames = ($Profiles -join " " -split '[,\s]+' | Where-Object { $_ })
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
if ($selectedProfiles.Count -gt 1 -and $DelayTo -eq 0) {
    if (Test-IsYouTubeUrl -Value $Url) {
        $DelayFrom = 20
        $DelayTo = 90
        Write-Host "Using default YouTube launch stagger: $DelayFrom-$DelayTo seconds."
    } elseif ($shouldSyncWebShare) {
        $DelayFrom = 30
        $DelayTo = 120
        Write-Host "Using default WebShare launch stagger: $DelayFrom-$DelayTo seconds."
    }
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
    $debugPort = Get-ProfileDebugPort -ProfileName $profileName -FallbackIndex ($started + 1)
    $windowSize = Get-ProfileSetting -EnvValues $profileEnv -ProfileName $profileName -Key "WINDOW_SIZE" -DefaultValue "1000,760"
    $language = Get-ProfileSetting -EnvValues $profileEnv -ProfileName $profileName -Key "LANGUAGE" -DefaultValue ""
    $acceptLanguage = Get-ProfileSetting -EnvValues $profileEnv -ProfileName $profileName -Key "ACCEPT_LANGUAGE" -DefaultValue $language
    $timezone = Get-ProfileSetting -EnvValues $profileEnv -ProfileName $profileName -Key "TIMEZONE" -DefaultValue ""
    $userAgent = Get-ProfileSetting -EnvValues $profileEnv -ProfileName $profileName -Key "USER_AGENT" -DefaultValue ""

    $launchUrl = if ($Mode -eq "login") { "https://accounts.google.com/" } else { $Url }
    if ($launchUrl -eq "profile-home") {
        $launchUrl = New-ProfileHome -ProfileName $profileName -Proxy $proxy -Upstream $upstream
    } else {
        $launchUrl = Add-YouTubeQualityParams -Value $launchUrl -Quality $VideoQuality
    }
    $extensionDir = New-PlaybackExtension `
        -ExtensionDir (Join-Path $RuntimeDir "extensions\$profileName") `
        -RefererValue $Referer `
        -Quality $VideoQuality `
        -AcceptLanguage $acceptLanguage

    $position = $profileEnv["WINDOW_POSITION_$profileName"]
    $chromeArgs = @(
        "--user-data-dir=$profileDir",
        "--proxy-server=$proxy",
        "--remote-debugging-address=127.0.0.1",
        "--remote-debugging-port=$debugPort",
        "--no-first-run",
        "--no-default-browser-check",
        "--autoplay-policy=no-user-gesture-required",
        "--force-webrtc-ip-handling-policy=disable_non_proxied_udp",
        "--window-size=$windowSize",
        "--new-window",
        $launchUrl
    )
    if ($language) {
        $chromeArgs = @("--lang=$language") + $chromeArgs
    }
    if ($acceptLanguage) {
        $chromeArgs = @("--accept-lang=$acceptLanguage") + $chromeArgs
    }
    if ($timezone) {
        $chromeArgs = @("--force-time-zone-for-testing=$timezone") + $chromeArgs
    }
    if ($userAgent) {
        $chromeArgs = @("--user-agent=$userAgent") + $chromeArgs
    }
    if ($position) {
        $chromeArgs = @("--window-position=$position") + $chromeArgs
    }
    if ($extensionDir) {
        $chromeArgs = @("--load-extension=$extensionDir", "--disable-extensions-except=$extensionDir") + $chromeArgs
    }
    if (Should-UseIncognito -Env $profileEnv -LaunchUrl $launchUrl) {
        $chromeArgs = @("--incognito") + $chromeArgs
    }

    $process = Start-Process -FilePath $ChromePath -ArgumentList $chromeArgs -PassThru
    Write-ProfileState -ProfileName $profileName -LaunchUrl $launchUrl -ProfileDir $profileDir -ProcessId $process.Id -DebugPort $debugPort -Mode $Mode -RefererValue $Referer -Quality $VideoQuality -Language $language -AcceptLanguage $acceptLanguage -Timezone $timezone -UserAgent $userAgent -WindowSize $windowSize
    $started++
    Write-Host "Started $profileName through $proxy"

    if ($started -lt $selectedProfiles.Count -and $DelayTo -gt 0) {
        $delay = if ($DelayTo -gt $DelayFrom) { Get-Random -Minimum $DelayFrom -Maximum ($DelayTo + 1) } else { $DelayFrom }
        Start-Sleep -Seconds $delay
    }
}

Write-Host "Started $started local profile(s)."
