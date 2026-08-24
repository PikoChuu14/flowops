param([string]$DotNet = 'dotnet', [string]$Runtime = 'win-x64', [string]$Version = '')

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$project = Join-Path $repo 'agent\FlowOps.NotificationAgent\FlowOps.NotificationAgent.csproj'
$output = Join-Path $repo "artifacts\agent\$Runtime"
if ([string]::IsNullOrWhiteSpace($Version)) { $Version = (Get-Content -Raw -LiteralPath (Join-Path $repo 'VERSION.txt')).Trim() }
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Invalid FlowOps version: $Version" }
& $DotNet publish $project -c Release -r $Runtime --self-contained true -p:Version=$Version -p:FileVersion=$Version -p:AssemblyVersion=$Version -p:PublishSingleFile=false -p:IncludeNativeLibrariesForSelfExtract=false -o $output
if ($LASTEXITCODE -ne 0) { throw 'Notification Agent publish failed.' }
Copy-Item -LiteralPath (Join-Path $repo 'installer\FlowOps.ico') -Destination (Join-Path $output 'FlowOps.ico') -Force
Copy-Item -LiteralPath (Join-Path $repo 'client\FlowOps-Client.ps1') -Destination (Join-Path $output 'FlowOps-Client.ps1') -Force
