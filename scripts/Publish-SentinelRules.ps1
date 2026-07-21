[CmdletBinding()]
param(
    [string]$EnvironmentFile,
    [string]$RuleDirectory
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if ([string]::IsNullOrWhiteSpace($EnvironmentFile)) {
    $EnvironmentFile = Join-Path $root '.env.local'
}
if ([string]::IsNullOrWhiteSpace($RuleDirectory)) {
    $RuleDirectory = Join-Path $root 'config\sentinel'
}

if (Test-Path -LiteralPath $EnvironmentFile) {
    foreach ($line in Get-Content -LiteralPath $EnvironmentFile -Encoding utf8) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $name, $value = $line -split '=', 2
        if ($name.Trim() -match '^[A-Za-z_][A-Za-z0-9_]*$') {
            [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
        }
    }
}

function Get-Setting {
    param([string]$Name, [string]$Default = '')
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) { return $Default }
    return $value
}

$address = Get-Setting 'NACOS_ADDR' '192.168.1.4:8848'
$baseUrl = if ($address -match '^https?://') { $address.TrimEnd('/') } else { "http://$address" }
$username = Get-Setting 'NACOS_USERNAME' 'nacos'
$password = Get-Setting 'NACOS_PASSWORD' 'nacos'
$group = Get-Setting 'NACOS_GROUP' 'AI_AUDIT'
$namespace = Get-Setting 'NACOS_NAMESPACE'

$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/nacos/v1/auth/login" -Body @{
    username = $username
    password = $password
}
$accessToken = $login.accessToken
if ([string]::IsNullOrWhiteSpace($accessToken)) { throw 'Nacos login returned no access token' }

$rules = @(
    'gateway-service-gw-api-group.json',
    'gateway-service-gw-flow.json',
    'gateway-service-degrade.json'
)

foreach ($dataId in $rules) {
    $path = Join-Path $RuleDirectory $dataId
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing Sentinel rule: $path" }
    $body = @{
        dataId = $dataId
        group = $group
        content = Get-Content -LiteralPath $path -Raw -Encoding utf8
        type = 'json'
        accessToken = $accessToken
    }
    if (-not [string]::IsNullOrWhiteSpace($namespace)) { $body.tenant = $namespace }
    $published = Invoke-RestMethod -Method Post -Uri "$baseUrl/nacos/v1/cs/configs" -Body $body
    if ($published -ne $true -and $published -ne 'true') { throw "Nacos rejected Sentinel rule: $dataId" }
    Write-Host "Published $dataId to Nacos group $group"
}
