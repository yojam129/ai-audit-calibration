param([string[]]$Service = @('all'))

$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'ServiceCatalog.ps1')
if ($Service -contains 'all') { $Service = @($ServiceCatalog.Keys) }

foreach ($name in $Service) {
    if (-not $ServiceCatalog.Contains($name)) { throw "Unknown service: $name" }
    $port = $ServiceCatalog[$name].Port
    $path = if ($ServiceCatalog[$name].Type -eq 'python') { '/health' } else { '/actuator/health' }
    try {
        $response = Invoke-WebRequest "http://localhost:$port$path" -TimeoutSec 3 -UseBasicParsing
        [pscustomobject]@{ Service = $name; Port = $port; Status = $response.StatusCode; Healthy = $true }
    } catch {
        [pscustomobject]@{ Service = $name; Port = $port; Status = $null; Healthy = $false }
    }
}
