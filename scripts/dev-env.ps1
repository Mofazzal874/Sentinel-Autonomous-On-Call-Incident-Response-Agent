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

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
java -version

