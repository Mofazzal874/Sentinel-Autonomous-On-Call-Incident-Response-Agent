$ErrorActionPreference = 'Stop'

$jdkRoot = 'E:\DevTools\temurin-25'
$gradleCache = 'E:\DevCaches\gradle'
$jdkHome = Get-ChildItem -LiteralPath $jdkRoot -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
    Sort-Object Name -Descending |
    Select-Object -First 1

if (-not $jdkHome) {
    throw "Java 25 was not found under $jdkRoot. Complete Phase 0 setup first."
}

$env:JAVA_HOME = $jdkHome.FullName
$env:GRADLE_USER_HOME = $gradleCache
$env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"

$localState = Join-Path $PSScriptRoot '..\.sentinel'
$localSecrets = Join-Path $localState 'dev-secrets.ps1'
if (-not (Test-Path -LiteralPath $localSecrets)) {
    New-Item -ItemType Directory -Path $localState -Force | Out-Null
    $jwtBytes = [byte[]]::new(32)
    $webhookBytes = [byte[]]::new(32)
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($jwtBytes)
        $random.GetBytes($webhookBytes)
    } finally {
        $random.Dispose()
    }
    $jwtSecret = [Convert]::ToBase64String($jwtBytes)
    $webhookSecret = [Convert]::ToBase64String($webhookBytes)
    $secretFile = @"
`$env:SENTINEL_JWT_SECRET='$jwtSecret'
`$env:SENTINEL_WEBHOOK_SECRET='$webhookSecret'
"@
    [IO.File]::WriteAllText($localSecrets, $secretFile, [Text.UTF8Encoding]::new($false))
}
. $localSecrets

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
Write-Host "Sentinel local security secrets loaded from E: project storage"
java -version
