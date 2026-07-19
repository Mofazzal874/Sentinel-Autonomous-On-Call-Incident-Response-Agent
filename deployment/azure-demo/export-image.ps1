[CmdletBinding()]
param([string]$OutputPath)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $OutputPath) { $OutputPath = Join-Path $repoRoot '.sentinel\sentinel-azure-demo.tar' }
$outputDirectory = Split-Path -Parent $OutputPath
[IO.Directory]::CreateDirectory($outputDirectory) | Out-Null

docker image inspect sentinel:azure-demo *> $null
if ($LASTEXITCODE -ne 0) { throw 'Build sentinel:azure-demo with test-local.ps1 before exporting it.' }

docker save --output $OutputPath sentinel:azure-demo
if ($LASTEXITCODE -ne 0) { throw 'Docker image export failed.' }
Write-Host "Exported sentinel:azure-demo to $OutputPath"
