$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$backend = Join-Path $root 'backend'; $frontend = Join-Path $root 'frontend'; $installer = Join-Path $root 'installer'; $payload = Join-Path $installer 'payload'
$versionFile = Join-Path $root 'VERSION.txt'
if(-not (Test-Path -LiteralPath $versionFile -PathType Leaf)){
  throw 'ERROR: VERSION.txt is missing or invalid. Expected a semantic version such as 1.1.0.'
}
$appVersion = (Get-Content -Raw -LiteralPath $versionFile).Trim()
if($appVersion -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)\z'){
  throw 'ERROR: VERSION.txt is missing or invalid. Expected a semantic version such as 1.1.0.'
}
$installerFileName = "FlowOps-Setup-$appVersion.exe"
$installerOutput = Join-Path $root "dist\installer\$installerFileName"
Write-Host "Building FlowOps v$appVersion"
function Find-Tool([string]$Name,[string[]]$Fallbacks) { $cmd=Get-Command $Name -ErrorAction SilentlyContinue; if($cmd){return $cmd.Source}; foreach($p in $Fallbacks){if(Test-Path $p){return $p}}; return $null }
$iscc = Find-Tool 'ISCC.exe' @('C:\Program Files (x86)\Inno Setup 6\ISCC.exe','C:\Program Files\Inno Setup 6\ISCC.exe')
$jlink = Find-Tool 'jlink.exe' @()
if(-not $iscc){ throw 'Inno Setup compiler ISCC.exe was not found. Install Inno Setup 6, then rerun scripts\build-installer.bat.' }
if(-not $jlink){ throw 'jlink.exe was not found. Run this build with a Java 21 JDK.' }
$winsw = Join-Path $installer 'prerequisites\WinSW-x64.exe'; if(-not (Test-Path $winsw)){ throw 'installer\prerequisites\WinSW-x64.exe is required. Place the approved WinSW binary there; it is not bundled by this repository.' }
$postgresInstaller = Join-Path $installer 'prerequisites\postgresql-installer.exe'; if(-not (Test-Path $postgresInstaller)){ throw 'installer\prerequisites\postgresql-installer.exe is required for the automatic PostgreSQL option. Place the official installer there; it is not bundled by this repository.' }
$windowsPowerShell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
Write-Host '[0/6] Validating installer scripts with Windows PowerShell 5.1...'
& $windowsPowerShell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'validate-windows-powershell.ps1') -Directory (Join-Path $installer 'scripts')
if($LASTEXITCODE -ne 0){throw 'Installer scripts are not compatible with Windows PowerShell 5.1'}
if(Test-Path -LiteralPath $installerOutput){
  Remove-Item -Force -LiteralPath $installerOutput
  if(Test-Path -LiteralPath $installerOutput){throw "Could not remove previous installer: $installerOutput"}
}
Write-Host '[1/6] Building frontend...'
Push-Location $frontend
try {
  npm ci
  if($LASTEXITCODE -ne 0){throw 'Frontend dependency installation failed'}
  npm run build
  if($LASTEXITCODE -ne 0){throw 'Frontend build failed'}
} finally {
  Pop-Location
}
Write-Host '[2/6] Building Spring Boot JAR...'; Push-Location $backend; & .\mvnw.cmd -DskipTests clean package; if($LASTEXITCODE -ne 0){throw 'Backend package failed'}; Pop-Location
Write-Host '[3/6] Preparing installer payload...'
New-Item -ItemType Directory -Force -Path "$payload\app","$payload\tools","$payload\prerequisites" | Out-Null
Copy-Item "$backend\target\flowops.jar" "$payload\app\flowops.jar" -Force
Copy-Item "$installer\FlowOps.xml" "$payload\FlowOps.xml" -Force
Copy-Item $winsw "$payload\FlowOps.exe" -Force
Copy-Item $postgresInstaller "$payload\prerequisites\postgresql-installer.exe" -Force
Copy-Item "$installer\scripts\setup-database.ps1","$installer\scripts\jwt-secret.ps1","$installer\scripts\backup-installed.ps1","$installer\scripts\restore-installed.ps1","$installer\scripts\restore-request.ps1","$installer\scripts\detect-postgresql.ps1","$installer\scripts\inspect-flowops.ps1","$installer\scripts\wait-for-ready.ps1" "$payload\tools" -Force
Write-Host '[OK]'
Write-Host '[4/6] Creating private Java 21 runtime...'
$runtimeDir = Join-Path $payload 'runtime'
if(Test-Path $runtimeDir){Remove-Item -Recurse -Force $runtimeDir}
& $jlink --add-modules java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,jdk.crypto.ec,jdk.unsupported --output $runtimeDir --strip-debug --no-header-files --no-man-pages --compress=2
if($LASTEXITCODE -ne 0){throw 'jlink runtime creation failed'}
if(-not (Test-Path (Join-Path $runtimeDir 'bin\java.exe'))){throw "jlink completed but runtime\bin\java.exe is missing: $runtimeDir"}
Write-Host '[OK] runtime\bin\java.exe'
Write-Host '[5/6] Compiling Inno Setup installer...'; & $iscc "/DAppVersion=$appVersion" "$installer\FlowOps.iss"; if($LASTEXITCODE -ne 0){throw 'Inno Setup compilation failed'}
if(-not (Test-Path -LiteralPath $installerOutput)){throw "Inno Setup reported success but the installer was not created: $installerOutput"}
$installerTimestamp = (Get-Item -LiteralPath $installerOutput).LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss zzz')
Write-Host '[6/6] Installer ready'
Write-Host "Installer build timestamp: $installerTimestamp"
Write-Host 'Installer created:'
Write-Host "dist\installer\$installerFileName"
