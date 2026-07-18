param(
    [ValidateSet('VIEWER', 'SRE_APPROVER', 'ADMIN', 'AGENT')]
    [string]$Role = 'VIEWER',

    [ValidatePattern('^[a-zA-Z0-9._-]{1,120}$')]
    [string]$Subject = 'local-user',

    [ValidateRange(1, 60)]
    [int]$LifetimeMinutes = 15
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'dev-env.ps1')

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$now = [DateTimeOffset]::UtcNow
$header = @{ alg = 'HS256'; typ = 'JWT' } | ConvertTo-Json -Compress
$payload = @{
    iss = if ($env:SENTINEL_JWT_ISSUER) { $env:SENTINEL_JWT_ISSUER } else { 'sentinel-local' }
    sub = $Subject
    aud = @($(if ($env:SENTINEL_JWT_AUDIENCE) { $env:SENTINEL_JWT_AUDIENCE } else { 'sentinel-api' }))
    iat = $now.ToUnixTimeSeconds()
    exp = $now.AddMinutes($LifetimeMinutes).ToUnixTimeSeconds()
    roles = @($Role)
} | ConvertTo-Json -Compress

$headerPart = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))
$payloadPart = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload))
$signingInput = "$headerPart.$payloadPart"
$hmac = [Security.Cryptography.HMACSHA256]::new(
    [Convert]::FromBase64String($env:SENTINEL_JWT_SECRET))
try {
    $signature = ConvertTo-Base64Url $hmac.ComputeHash([Text.Encoding]::ASCII.GetBytes($signingInput))
} finally {
    $hmac.Dispose()
}

Write-Output "$signingInput.$signature"
