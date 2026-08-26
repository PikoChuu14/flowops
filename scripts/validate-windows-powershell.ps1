param([Parameter(Mandatory=$true)][string]$Directory)

$hasErrors = $false
foreach ($scriptPath in Get-ChildItem -LiteralPath $Directory -Filter '*.ps1' | Select-Object -ExpandProperty FullName) {
  $tokens = $null
  $parseErrors = $null
  [System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$parseErrors) | Out-Null
  foreach ($parseError in $parseErrors) {
    Write-Error "$scriptPath`:$($parseError.Extent.StartLineNumber): $($parseError.Message)"
    $hasErrors = $true
  }
}

if ($hasErrors) { exit 1 }
Write-Output 'Windows PowerShell 5.1 syntax validation passed.'
