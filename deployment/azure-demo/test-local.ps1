[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$environmentFile = Join-Path $repoRoot '.sentinel\azure-demo-local.env'
$composeFile = Join-Path $PSScriptRoot 'compose.yaml'
$projectName = 'sentinel-azure-local'

& (Join-Path $PSScriptRoot 'new-local-env.ps1')

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI is not installed or is not on PATH.'
}
docker version --format '{{.Server.Version}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker Engine is not running.' }

$ollamaTags = Invoke-RestMethod -Uri 'http://127.0.0.1:11434/api/tags' -TimeoutSec 10
$modelNames = @($ollamaTags.models.name)
foreach ($requiredModel in @('qwen3:4b', 'nomic-embed-text:latest')) {
    if ($requiredModel -notin $modelNames) { throw "Required local Ollama model is missing: $requiredModel" }
}

$env:JAVA_HOME = 'E:\DevTools\temurin-25\jdk-25.0.3+9'
$env:GRADLE_USER_HOME = 'E:\DevCaches\gradle'
if (-not (Test-Path -LiteralPath $env:JAVA_HOME)) { throw "Java 25 was not found at $env:JAVA_HOME" }

Push-Location $repoRoot
try {
    $env:npm_config_cache = 'E:\DevCaches\npm'
    & npm --prefix frontend ci --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'Frontend npm ci failed.' }

    & npm --prefix frontend run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend production export failed.' }

    & .\gradlew.bat bootJar
    if ($LASTEXITCODE -ne 0) { throw 'Gradle bootJar failed.' }

    docker build --tag sentinel:azure-demo .
    if ($LASTEXITCODE -ne 0) { throw 'Docker image build failed.' }

    docker compose --project-name $projectName --env-file $environmentFile --file $composeFile up --detach --wait
    if ($LASTEXITCODE -ne 0) { throw 'Deployment rehearsal services did not become healthy.' }

    $ready = $false
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $status = & curl.exe --silent --output NUL --write-out '%{http_code}' --max-time 3 'http://127.0.0.1:18080/actuator/health/readiness'
        if ($status -eq '200') { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Sentinel did not become ready within 120 seconds.' }

    $livenessStatus = & curl.exe --silent --output NUL --write-out '%{http_code}' --max-time 5 'http://127.0.0.1:18080/actuator/health/liveness'
    if ($livenessStatus -ne '200') { throw "Liveness probe returned HTTP $livenessStatus." }

    $prometheusStatus = & curl.exe --silent --output NUL --write-out '%{http_code}' --max-time 5 'http://127.0.0.1:18080/actuator/prometheus'
    if ($prometheusStatus -ne '401') { throw "Prometheus endpoint returned HTTP $prometheusStatus instead of 401." }

    $embeddingCount = docker compose --project-name $projectName --env-file $environmentFile --file $composeFile exec --no-TTY postgres psql --username sentinel --dbname sentinel --tuples-only --no-align --command 'SELECT count(*) FROM runbook_embedding;'
    if ($LASTEXITCODE -ne 0 -or $embeddingCount.Trim() -ne '3') {
        throw "Expected 3 indexed runbooks, received: $embeddingCount"
    }

    $consoleStatus = & curl.exe --silent --output NUL --write-out '%{http_code}' --max-time 5 'http://127.0.0.1:18080/'
    if ($consoleStatus -ne '200') { throw "Operator console returned HTTP $consoleStatus." }

    $demoRunsStatus = & curl.exe --silent --output NUL --write-out '%{http_code}' --max-time 5 'http://127.0.0.1:18080/api/v1/demo/runs'
    if ($demoRunsStatus -ne '200') { throw "Demo runs API returned HTTP $demoRunsStatus." }

    Write-Host 'Local deployment rehearsal passed: console=200, demo-runs=200, readiness=200, liveness=200, Prometheus=401, indexed-runbooks=3.'
    Write-Host 'The isolated rehearsal stack is still running. Use stop-local.ps1 when finished.'
} catch {
    docker compose --project-name $projectName --env-file $environmentFile --file $composeFile ps
    docker compose --project-name $projectName --env-file $environmentFile --file $composeFile logs --tail 120 sentinel
    throw
} finally {
    Pop-Location
}
