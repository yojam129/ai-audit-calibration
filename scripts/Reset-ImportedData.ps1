[CmdletBinding()]
param(
    [switch]$Execute,
    [string]$Confirmation,
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '..\.env.local'),
    [string]$RabbitManagementPort = '15672',
    [string]$PythonExecutable = (Join-Path $PSScriptRoot '..\ai-inference-service\.venv\Scripts\python.exe')
)

$ErrorActionPreference = 'Stop'
$requiredConfirmation = 'DELETE-IMPORTED-DATA'

function Import-EnvironmentFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $name, $value = $line -split '=', 2
        if ($name.Trim() -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            throw "Invalid environment variable name in ${Path}: $name"
        }
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
    }
}

function Get-Setting {
    param([string]$Name, [string]$Default = '')
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) { return $Default }
    return $value
}

function Write-Action {
    param([string]$Message)
    if ($Execute) { Write-Host "[EXECUTE] $Message" -ForegroundColor Yellow }
    else { Write-Host "[DRY-RUN] $Message" -ForegroundColor Cyan }
}

function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is unavailable: $Name"
    }
}

function Invoke-MySqlCleanup {
    param([string]$Schema, [string[]]$Tables)
    Write-Action "MySQL ${Schema}: delete rows from $($Tables -join ', ')"
    if (-not $Execute) { return }

    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = Get-Setting 'MYSQL_PASSWORD'
        $mysqlHost = Get-Setting 'MYSQL_HOST' '192.168.1.4'
        $mysqlPort = Get-Setting 'MYSQL_PORT' '3306'
        $mysqlUser = Get-Setting 'MYSQL_USERNAME' 'root'
        $existingTables = @(& mysql "--host=$mysqlHost" "--port=$mysqlPort" "--user=$mysqlUser" `
            '--batch' '--skip-column-names' `
            "--execute=SELECT table_name FROM information_schema.tables WHERE table_schema='$Schema';")
        if ($LASTEXITCODE -ne 0) { throw "MySQL table discovery failed for $Schema" }
        $statements = @('SET FOREIGN_KEY_CHECKS=0')
        foreach ($table in $Tables) {
            if ($existingTables -contains $table) { $statements += "DELETE FROM ``$table``" }
        }
        $statements += 'SET FOREIGN_KEY_CHECKS=1'
        $sql = ($statements -join ';') + ';'
        & mysql "--host=$mysqlHost" "--port=$mysqlPort" "--user=$mysqlUser" `
            "--database=$Schema" "--execute=$sql"
        if ($LASTEXITCODE -ne 0) { throw "MySQL cleanup failed for $Schema" }
    } finally {
        $env:MYSQL_PWD = $previousPassword
    }
}

function New-BasicAuthHeader {
    param([string]$Username, [string]$Password)
    $bytes = [Text.Encoding]::ASCII.GetBytes("${Username}:${Password}")
    return @{ Authorization = 'Basic ' + [Convert]::ToBase64String($bytes) }
}

function Remove-FlowableInstances {
    $baseUrl = (Get-Setting 'FLOWABLE_REST_URL' 'http://192.168.1.4:8090/flowable-ui/process-api').TrimEnd('/')
    $processKeys = @('sampleAuditMain', 'reviewerQualification', 'importRecovery', 'secondaryReview')
    Write-Action "Flowable: delete runtime and historic business instances for $($processKeys -join ', '); keep deployments"
    if (-not $Execute) { return }
    $headers = New-BasicAuthHeader (Get-Setting 'FLOWABLE_REST_USERNAME' 'admin') (Get-Setting 'FLOWABLE_REST_PASSWORD' 'test')

    foreach ($processKey in $processKeys) {
        $runtime = Invoke-RestMethod -Method Get -Uri "$baseUrl/runtime/process-instances?processDefinitionKey=$processKey&size=1000" -Headers $headers
        foreach ($instance in @($runtime.data)) {
            Invoke-RestMethod -Method Delete -Uri "$baseUrl/runtime/process-instances/$($instance.id)?deleteReason=import-data-reset" -Headers $headers | Out-Null
        }

        $history = Invoke-RestMethod -Method Get -Uri "$baseUrl/history/historic-process-instances?processDefinitionKey=$processKey&size=1000" -Headers $headers
        foreach ($instance in @($history.data)) {
            Invoke-RestMethod -Method Delete -Uri "$baseUrl/history/historic-process-instances/$($instance.id)" -Headers $headers | Out-Null
        }
    }
}

function Clear-RabbitQueues {
    $queues = @(
        'alert.comparison.v1', 'alert.comparison.v1.dlq', 'alert.detection-target.v1',
        'review.comparison.v1', 'statistics.truth.v1', 'statistics.truth.v1.dlq',
        'risk.reviewer-outcome.v1', 'risk.ground-truth.v1',
        'signal.ai-training-feedback.v1', 'sample.review.archived.v1',
        'review.audit.imported.v1', 'review.audit.ai-completed.v1',
        'review.audit.primary-completed.v1',
        'learning.training-trigger.v1',
        'notification.domain.v1', 'notification.domain.v1.dlq'
    )
    Write-Action "RabbitMQ: purge queues $($queues -join ', ')"
    if (-not $Execute) { return }
    $hostName = Get-Setting 'RABBITMQ_HOST' '192.168.1.4'
    $headers = New-BasicAuthHeader (Get-Setting 'RABBITMQ_USERNAME' 'guest') (Get-Setting 'RABBITMQ_PASSWORD')
    foreach ($queue in $queues) {
        $encodedQueue = [Uri]::EscapeDataString($queue)
        $uri = "http://${hostName}:${RabbitManagementPort}/api/queues/%2F/${encodedQueue}/contents"
        try {
            Invoke-RestMethod -Method Delete -Uri $uri -Headers $headers | Out-Null
        } catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            if ($statusCode -ne 404) { throw }
        }
    }
}

function Clear-RedisData {
    $patterns = @('ai-audit:statistics:v1:dashboard', 'yo:audit:idempotency:*')
    Write-Action "Redis: delete imported-data cache keys matching $($patterns -join ', '); preserve auth/version/refresh keys"
    if (-not $Execute) { return }
    $commonArgs = @('-h', (Get-Setting 'REDIS_HOST' '192.168.1.4'), '-p', (Get-Setting 'REDIS_PORT' '6379'))
    $password = Get-Setting 'REDIS_PASSWORD'
    if ($password) { $commonArgs += @('-a', $password, '--no-auth-warning') }
    foreach ($pattern in $patterns) {
        $keys = @(& redis-cli @commonArgs --scan --pattern $pattern)
        foreach ($key in $keys) {
            if ($key) { & redis-cli @commonArgs UNLINK $key | Out-Null }
        }
    }
}

function Clear-MongoCurves {
    $uri = Get-Setting 'SIGNAL_MONGODB_URI' 'mongodb://192.168.1.4:27017/ai_audit_signal'
    Write-Action 'MongoDB ai_audit_signal.fluorescence_curve: delete all documents; preserve indexes and ai_training_feedback'
    if (-not $Execute) { return }
    & mongosh $uri --quiet --eval "db.getCollection('fluorescence_curve').deleteMany({})"
    if ($LASTEXITCODE -ne 0) { throw 'MongoDB cleanup failed' }
}

function Clear-ElasticsearchTrace {
    $baseUrl = (Get-Setting 'ELASTICSEARCH_URIS' 'http://192.168.1.4:9200').Split(',')[0].TrimEnd('/')
    Write-Action 'Elasticsearch ai-audit-trace: delete all documents; preserve index mapping'
    if (-not $Execute) { return }
    $body = '{"query":{"match_all":{}}}'
    try {
        Invoke-RestMethod -Method Post -Uri "$baseUrl/ai-audit-trace/_delete_by_query?conflicts=proceed&refresh=true" -ContentType 'application/json' -Body $body | Out-Null
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -ne 404) { throw }
    }
}

function Clear-MinioObjects {
    $incomingBucket = Get-Setting 'MINIO_INCOMING_BUCKET' 'ai-audit-incoming'
    $archiveBucket = Get-Setting 'TRACE_ARCHIVE_BUCKET' 'ai-audit-archive'
    Write-Action "MinIO: delete all objects under ${incomingBucket}/ and ${archiveBucket}/audit-manifest/; preserve model artifacts"
    if (-not $Execute) { return }
    $configDir = Join-Path ([IO.Path]::GetTempPath()) ('ai-audit-mc-' + [Guid]::NewGuid())
    New-Item -ItemType Directory -Path $configDir | Out-Null
    try {
        & mc --config-dir $configDir alias set cleanup (Get-Setting 'MINIO_ENDPOINT' 'http://192.168.1.4:9000') (Get-Setting 'MINIO_ACCESS_KEY') (Get-Setting 'MINIO_SECRET_KEY') | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'MinIO alias setup failed' }
        & mc --config-dir $configDir stat "cleanup/${incomingBucket}" 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            & mc --config-dir $configDir rm --recursive --force "cleanup/${incomingBucket}/" | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'MinIO incoming cleanup failed' }
        }
        & mc --config-dir $configDir stat "cleanup/${archiveBucket}" 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            & mc --config-dir $configDir rm --recursive --force "cleanup/${archiveBucket}/audit-manifest/" | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'MinIO archive cleanup failed' }
        }
    } finally {
        Remove-Item -LiteralPath $configDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Clear-ExternalStores {
    Write-Action 'MongoDB curves, imported-data Redis keys, and MinIO import/audit objects; preserve model artifacts'
    if (-not $Execute) { return }
    if (-not (Test-Path -LiteralPath $PythonExecutable -PathType Leaf)) {
        throw "Python runtime not found: $PythonExecutable"
    }
    & $PythonExecutable (Join-Path $PSScriptRoot 'reset_external_data.py') --execute
    if ($LASTEXITCODE -ne 0) { throw 'External store cleanup failed' }
}

Import-EnvironmentFile $EnvironmentFile

if ($Execute -and $Confirmation -ne $requiredConfirmation) {
    throw "Execution requires -Confirmation '$requiredConfirmation'."
}

if ($Execute) {
    Assert-Command 'mysql'
}

Write-Host 'Imported data reset scope:' -ForegroundColor Green
Write-Host '  Preserve: ai_audit_auth, model registry schema/artifacts, training feedback, Flyway histories, notification preferences, exam questions, Flowable deployments.'
Write-Host '  Delete: imported source records and every derived workflow/projection/audit record.'

# Stop consumers before execution. Clearing queues first prevents old events from recreating rows.
Clear-RabbitQueues
Remove-FlowableInstances

$schemas = [ordered]@{
    ai_audit_integration = @('import_error', 'import_row_task', 'import_batch', 'file_asset')
    ai_audit_sample = @('sample_outbox', 'primary_review_task', 'target_judgement', 'instrument_run', 'detection_order', 'sample', 'cartridge', 'reagent_lot')
    ai_audit_signal = @('inference_outbox', 'ai_inference_result', 'signal_index')
    ai_audit_judgement = @('judgement_outbox', 'comparison_run', 'judgement_target', 'judgement')
    ai_audit_review = @('review_outbox', 'ground_truth', 'review_task')
    ai_audit_alert = @('positive_rate_alert', 'detection_target_fact', 'alert_consumed_event', 'alert')
    ai_audit_statistics = @('daily_accuracy_projection', 'confusion_projection', 'accuracy_projection', 'ground_truth_outcome_fact', 'statistics_consumed_event')
    risk_control = @('risk_outbox', 'risk_consumed_event', 'reviewer_error_focus', 'risk_profile')
    learning = @('exam_answer', 'exam_attempt', 'learning_outbox', 'learning_assignment')
    trace = @('trace_outbox', 'trace_record')
    notification = @('notification_record')
    ai_audit_scheduler = @('scheduler_job_execution')
}

foreach ($entry in $schemas.GetEnumerator()) {
    Invoke-MySqlCleanup $entry.Key $entry.Value
}

Clear-ElasticsearchTrace
Clear-ExternalStores

if ($Execute) { Write-Host 'Imported data cleanup completed.' -ForegroundColor Green }
else { Write-Host "Dry-run only. Execute with -Execute -Confirmation '$requiredConfirmation'." -ForegroundColor Green }
