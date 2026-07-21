param([string[]]$Service = @('all'))

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'ServiceCatalog.ps1')
if ($Service -contains 'all') { $Service = @($ServiceCatalog.Keys) }
$pidRoot = Join-Path $root '.run/pids'

foreach ($name in $Service) {
    if (-not $ServiceCatalog.Contains($name)) { throw "Unknown service: $name" }
    $pidFile = Join-Path $pidRoot "$name.pid"
    if (-not (Test-Path $pidFile)) { Write-Host "$name is not tracked"; continue }
    $processId = [int](Get-Content -LiteralPath $pidFile)
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process) {
        Stop-Process -Id $processId
        if (-not $process.WaitForExit(10000)) { Stop-Process -Id $processId -Force }
    }
    Remove-Item -LiteralPath $pidFile -Force
    Write-Host "Stopped $name"
}
