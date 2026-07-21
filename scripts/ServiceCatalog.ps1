$ServiceCatalog = [ordered]@{
    'model-registry-service'  = @{ Port = 18094; Type = 'java' }
    'auth-service'            = @{ Port = 18081; Type = 'java' }
    'ai-inference-service'    = @{ Port = 18000; Type = 'python' }
    'sample-service'          = @{ Port = 18082; Type = 'java' }
    'signal-service'          = @{ Port = 18083; Type = 'java' }
    'judgement-service'       = @{ Port = 18084; Type = 'java' }
    'alert-service'           = @{ Port = 18085; Type = 'java' }
    'review-workflow-service' = @{ Port = 18086; Type = 'java' }
    'risk-control-service'    = @{ Port = 18087; Type = 'java' }
    'trace-service'           = @{ Port = 18089; Type = 'java' }
    'notification-service'    = @{ Port = 18090; Type = 'java' }
    'learning-service'        = @{ Port = 18091; Type = 'java' }
    'statistics-service'      = @{ Port = 18092; Type = 'java' }
    'integration-service'     = @{ Port = 18093; Type = 'java' }
    'scheduler-service'       = @{ Port = 18095; Type = 'java' }
    'gateway-service'         = @{ Port = 18088; Type = 'java' }
}

function Import-LocalEnvironment {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $name, $value = $line -split '=', 2
        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            throw "Invalid environment variable name in ${Path}: $name"
        }
        [Environment]::SetEnvironmentVariable($name.Trim(), $value, 'Process')
    }
}
