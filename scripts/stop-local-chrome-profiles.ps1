param(
    [string]$RuntimeDir = "$env:USERPROFILE\chrome-proxy-profiles"
)

$ErrorActionPreference = "Stop"
$escapedRuntime = $RuntimeDir.Replace('\', '\\')

Get-CimInstance Win32_Process -Filter "name = 'chrome.exe'" |
    Where-Object { $_.CommandLine -match [regex]::Escape($RuntimeDir) } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped Chrome PID $($_.ProcessId)"
    }

Get-CimInstance Win32_Process -Filter "name = 'python.exe' OR name = 'python3.exe' OR name = 'py.exe'" |
    Where-Object { $_.CommandLine -match 'proxy-forwarder\.py' -and $_.CommandLine -match [regex]::Escape($RuntimeDir) } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped proxy forwarder PID $($_.ProcessId)"
    }

Get-CimInstance Win32_Process -Filter "name = 'powershell.exe'" |
    Where-Object { $_.CommandLine -match 'proxy-forwarder\.py' -and $_.CommandLine -match [regex]::Escape($RuntimeDir) } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped proxy wrapper PID $($_.ProcessId)"
    }
