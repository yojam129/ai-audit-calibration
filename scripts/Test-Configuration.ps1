$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'ServiceCatalog.ps1')

$duplicates =
    $ServiceCatalog.GetEnumerator() |
    Group-Object { $_.Value.Port } |
    Where-Object Count -gt 1
if ($duplicates) { throw "Duplicate application ports: $($duplicates.Name -join ', ')" }

$configurationFiles =
    Get-ChildItem $root -Recurse -Filter application.yml |
    Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' }
$problems = [System.Collections.Generic.List[string]]::new()
foreach ($file in $configurationFiles) {
    $content = Get-Content -LiteralPath $file.FullName -Raw
    if ($content -match 'localhost:(3306|6379|5672|27017|8848|9000|9200)') {
        $problems.Add("$($file.FullName): infrastructure defaults must use 192.168.1.4")
    }
    if ($content -match '\$\{[A-Z0-9_]*(PASSWORD|SECRET):[^}\r\n]+') {
        $problems.Add("$($file.FullName): possible non-empty secret default")
    }
}
foreach ($entry in $ServiceCatalog.GetEnumerator()) {
    if ($entry.Value.Type -ne 'java') { continue }
    $applicationFile =
        Join-Path $root "$($entry.Key)/src/main/resources/application.yml"
    if (-not (Test-Path -LiteralPath $applicationFile)) {
        $problems.Add("$($entry.Key): application.yml is missing")
        continue
    }
    $content = Get-Content -LiteralPath $applicationFile -Raw
    if ($content -notmatch "name:\s*$([regex]::Escape($entry.Key))") {
        $problems.Add("$($entry.Key): spring.application.name is missing or incorrect")
    }
}
if ($problems.Count -gt 0) {
    $problems | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    exit 1
}
Write-Host "Configuration static checks passed: $($configurationFiles.Count) YAML files, $($ServiceCatalog.Count) unique service ports."
