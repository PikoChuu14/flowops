$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$installer = Join-Path $root 'installer'
$version = (Get-Content -Raw -LiteralPath (Join-Path $root 'VERSION.txt')).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+(\.[0-9A-Za-z-]+)?$') { throw "Invalid FlowOps version: $version" }
$versionParts = $version -split '\.'
$fileVersion = "$($versionParts[0]).$($versionParts[1]).$($versionParts[2]).1"

function Find-Tool([string]$Name, [string[]]$Fallbacks) {
  $command = Get-Command $Name -ErrorAction SilentlyContinue
  if ($command) { return $command.Source }
  foreach ($path in $Fallbacks) { if (Test-Path -LiteralPath $path) { return $path } }
  return $null
}

$iscc = Find-Tool 'ISCC.exe' @(
  'C:\Program Files (x86)\Inno Setup 6\ISCC.exe',
  'C:\Program Files\Inno Setup 6\ISCC.exe'
)
if (-not $iscc) { throw 'Inno Setup compiler ISCC.exe was not found. Install Inno Setup 6, then rerun this script.' }

$localDotnet = Join-Path $root '.tools\dotnet\dotnet.exe'
$dotnet = if (Test-Path -LiteralPath $localDotnet) { $localDotnet } else { Find-Tool 'dotnet.exe' @() }
if (-not $dotnet) { throw 'A .NET 8 SDK was not found. Install the SDK or place it under .tools\dotnet, then rerun this script.' }
$dotnetWorkspace = Join-Path $root '.tools'
New-Item -ItemType Directory -Force -Path $dotnetWorkspace | Out-Null
$env:DOTNET_CLI_HOME = $dotnetWorkspace
$env:NUGET_PACKAGES = Join-Path $dotnetWorkspace 'nuget'
$env:DOTNET_SKIP_FIRST_TIME_EXPERIENCE = '1'
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
& (Join-Path $PSScriptRoot 'publish-agent.ps1') -DotNet $dotnet -Version $version

& $iscc "/DAppVersion=$version" "/DAppVersionNumeric=$fileVersion" (Join-Path $installer 'FlowOps-Client.iss')
if ($LASTEXITCODE -ne 0) { throw 'FlowOps Client installer compilation failed.' }

$output = Join-Path $root "dist\installer\FlowOps-Client-Setup-$version.exe"
if (-not (Test-Path -LiteralPath $output)) { throw "Installer output was not created: $output" }
Write-Host "FlowOps Client installer created: $output"
