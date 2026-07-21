[CmdletBinding()]
param(
    [string]$GatewayUrl = 'http://localhost:18088',
    [string]$Username = 'admin',
    [Parameter(Mandatory)]
    [SecureString]$Password,
    [string]$FluorescenceFile = ((Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '..\..') -Filter '*.xlsx' | Sort-Object Length -Descending | Select-Object -First 1).FullName),
    [string]$PositiveRateFile = ((Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '..\..') -Filter '*.xlsx' | Sort-Object Length | Select-Object -First 1).FullName),
    [switch]$SkipPositiveRate,
    [string]$TemplateVersion = '1.0'
)

$ErrorActionPreference = 'Stop'
$contentType = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

function Unwrap-ApiResponse {
    param($Response, [string]$Operation)
    if ($null -eq $Response) { throw "$Operation returned an empty response" }
    if ([int]$Response.code -ne 0) { throw "$Operation failed: $($Response.message)" }
    return $Response.data
}

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body, [hashtable]$Headers = @{})
    $parameters = @{
        Method = $Method
        Uri = $GatewayUrl.TrimEnd('/') + $Path
        Headers = $Headers
        ContentType = 'application/json'
    }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 8 }
    return Invoke-RestMethod @parameters
}

function Import-ExcelFile {
    param([string]$Path, [string]$BusinessType, [string]$AccessToken)
    $file = Get-Item -LiteralPath $Path
    $sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $headers = @{ Authorization = "Bearer $AccessToken"; 'X-Trace-Id' = [Guid]::NewGuid().ToString() }

    Write-Host "Uploading $($file.Name) as $BusinessType..." -ForegroundColor Cyan
    $presign = Unwrap-ApiResponse (Invoke-Api Post '/api/integration/files/presign' @{
        fileName = $file.Name
        contentType = $contentType
        sizeBytes = $file.Length
        sha256 = $sha256
    } $headers) 'Create presigned upload'

    Invoke-WebRequest -UseBasicParsing -Method Put -Uri $presign.uploadUrl -InFile $file.FullName -ContentType $contentType | Out-Null

    $asset = Unwrap-ApiResponse (Invoke-Api Post '/api/integration/files/confirm' @{
        assetId = $presign.assetId
        sizeBytes = $file.Length
        sha256 = $sha256
    } $headers) 'Confirm upload'

    $batch = Unwrap-ApiResponse (Invoke-Api Post '/api/integration/imports' @{
        assetId = $asset.id
        businessType = $BusinessType
        templateVersion = $TemplateVersion
    } $headers) 'Create import batch'

    Write-Host "Created import batch $($batch.batchNo), status=$($batch.status), rows=$($batch.totalRows)." -ForegroundColor Green
    return $batch
}

$requiredFiles = @($FluorescenceFile)
if (-not $SkipPositiveRate) { $requiredFiles += $PositiveRateFile }
foreach ($path in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Demo file not found: $path" }
}

$credential = [Net.NetworkCredential]::new('', $Password)
$login = Unwrap-ApiResponse (Invoke-Api Post '/auth/login' @{
    username = $Username
    password = $credential.Password
}) 'Login'
$accessToken = $login.tokens.accessToken
if ([string]::IsNullOrWhiteSpace($accessToken)) { throw 'Login response has no access token' }

$fluorescenceBatch = Import-ExcelFile $FluorescenceFile 'FLUORESCENCE_RAW' $accessToken
if (-not $SkipPositiveRate) {
    $positiveRateBatch = Import-ExcelFile $PositiveRateFile 'POSITIVE_RATE_HISTORY' $accessToken
}

Write-Host 'Selected demo imports were submitted. Row processing continues asynchronously.' -ForegroundColor Green
Write-Host 'Fluorescence idempotency: instrument_run.uk_run_no (runNo = instrument-startTime-module).'
Write-Host 'Positive-rate idempotency: detection_target_fact.uk_fact_order_target (organization-order-target).'
