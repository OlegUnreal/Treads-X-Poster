param(
    [Parameter(Position = 0, Mandatory = $true)]
    [int]$Count,

    [Parameter(Position = 1, Mandatory = $true)]
    [string]$Url
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$launcher = Join-Path $scriptDir "start-local-chrome-profiles.ps1"

& $launcher -Count $Count -Url $Url
