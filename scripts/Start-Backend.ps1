param(
    [Parameter(Mandatory = $true)]
    [string[]]$Service,
    [string]$EnvironmentFile = '.env.local',
    [int]$StartupBatchSize = 3,
    [int]$StartupBatchDelaySeconds = 8
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'ServiceCatalog.ps1')
Import-LocalEnvironment (Join-Path $root $EnvironmentFile)
$processEnvironment = [Environment]::GetEnvironmentVariables('Process')
$pathKeys = @($processEnvironment.Keys | Where-Object { $_ -ieq 'PATH' })
if ($pathKeys.Count -gt 1) {
    $pathValue = [Environment]::GetEnvironmentVariable('Path', 'Process')
    foreach ($pathKey in $pathKeys) {
        [Environment]::SetEnvironmentVariable([string]$pathKey, $null, 'Process')
    }
    [Environment]::SetEnvironmentVariable('Path', $pathValue, 'Process')
}
$runtime = Join-Path $root '.run'
$logs = Join-Path $runtime 'logs'
$pids = Join-Path $runtime 'pids'
New-Item -ItemType Directory -Force -Path $logs, $pids | Out-Null

$startAll = $Service -contains 'all'
if ($startAll) { $Service = @($ServiceCatalog.Keys) }
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
$ErrorActionPreference = $previousErrorActionPreference
if ($Service | Where-Object { $ServiceCatalog[$_].Type -eq 'java' }) {
    if ($javaVersion -notmatch '"21(\.|")') { throw "Java 21 is required; found: $javaVersion" }
}

function Wait-LocalPort {
    param([int]$Port, [int]$TimeoutSeconds = 90)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $connection = $client.ConnectAsync('127.0.0.1', $Port)
            if ($connection.Wait(1000) -and $client.Connected) { return }
        } catch {
        } finally {
            $client.Dispose()
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for local port $Port"
}

for ($serviceIndex = 0; $serviceIndex -lt $Service.Count; $serviceIndex++) {
    $name = $Service[$serviceIndex]
    if ($startAll -and $name -eq 'ai-inference-service') {
        Wait-LocalPort -Port $ServiceCatalog['model-registry-service'].Port
    }
    if (-not $ServiceCatalog.Contains($name)) { throw "Unknown service: $name" }
    $pidFile = Join-Path $pids "$name.pid"
    if (Test-Path $pidFile) {
        $existing = Get-Process -Id ([int](Get-Content $pidFile)) -ErrorAction SilentlyContinue
        if ($existing) { Write-Host "$name already running (PID $($existing.Id))"; continue }
        Remove-Item -LiteralPath $pidFile -Force
    }
    $stdout = Join-Path $logs "$name.out.log"
    $stderr = Join-Path $logs "$name.err.log"
    if ($ServiceCatalog[$name].Type -eq 'java') {
        $jar = Get-ChildItem (Join-Path $root "$name/target") -Filter "$name-*.jar" -File |
            Where-Object { $_.Name -notmatch '\.original$' } | Select-Object -First 1
        if (-not $jar) { throw "Package $name first; no runnable JAR found" }
        $process = Start-Process java -ArgumentList @('-jar', $jar.FullName) -WorkingDirectory $root `
            -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    } else {
        $python = Join-Path $root 'ai-inference-service/.venv/Scripts/python.exe'
        if (-not (Test-Path $python)) { throw 'Create ai-inference-service/.venv and install requirements first' }
        $pythonPort = $ServiceCatalog[$name].Port
        $process = Start-Process $python -ArgumentList @('-m', 'uvicorn', 'app.main:app', '--host', '0.0.0.0', '--port', $pythonPort) `
            -WorkingDirectory (Join-Path $root 'ai-inference-service') -WindowStyle Hidden `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    }
    Set-Content -LiteralPath $pidFile -Value $process.Id
    Write-Host "Started $name (PID $($process.Id)); logs: $stdout"
    $batchComplete = (($serviceIndex + 1) % [Math]::Max(1, $StartupBatchSize)) -eq 0
    $hasMoreServices = $serviceIndex -lt ($Service.Count - 1)
    if ($startAll -and $batchComplete -and $hasMoreServices) {
        Start-Sleep -Seconds ([Math]::Max(0, $StartupBatchDelaySeconds))
    }
}
