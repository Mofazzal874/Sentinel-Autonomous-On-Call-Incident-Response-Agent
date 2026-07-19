[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$secretDirectory = Join-Path $repoRoot '.sentinel'
$environmentFile = Join-Path $secretDirectory 'azure-demo-local.env'

if (Test-Path -LiteralPath $environmentFile) {
    Write-Host "Reusing existing ignored environment file: $environmentFile"
    exit 0
}

[IO.Directory]::CreateDirectory($secretDirectory) | Out-Null

function New-HexSecret([int]$byteCount) {
    $bytes = [byte[]]::new($byteCount)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return (-join ($bytes | ForEach-Object { $_.ToString('x2') }))
}

function New-Base64Secret([int]$byteCount) {
    $bytes = [byte[]]::new($byteCount)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

$lines = @(
    'SENTINEL_DB_NAME=sentinel'
    'SENTINEL_DB_USERNAME=sentinel'
    "SENTINEL_DB_PASSWORD=$(New-HexSecret 24)"
    'SENTINEL_RABBITMQ_USERNAME=sentinel'
    "SENTINEL_RABBITMQ_PASSWORD=$(New-HexSecret 24)"
    "SENTINEL_JWT_SECRET=$(New-Base64Secret 48)"
    "SENTINEL_WEBHOOK_SECRET=$(New-Base64Secret 48)"
    'SENTINEL_OLLAMA_BASE_URL=http://host.docker.internal:11434'
    'SENTINEL_APP_PORT=18080'
)

[IO.File]::WriteAllLines($environmentFile, $lines, [Text.UTF8Encoding]::new($false))
Write-Host "Created ignored local environment file: $environmentFile"
