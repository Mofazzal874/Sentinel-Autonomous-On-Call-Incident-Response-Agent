[CmdletBinding()]
param([switch]$DeleteData)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$environmentFile = Join-Path $repoRoot '.sentinel\azure-demo-local.env'
$arguments = @('compose', '--project-name', 'sentinel-azure-local', '--env-file', $environmentFile,
    '--file', (Join-Path $PSScriptRoot 'compose.yaml'), 'down', '--remove-orphans')

if ($DeleteData) {
    $arguments += '--volumes'
    Write-Host 'Deleting only the isolated sentinel-azure-local Docker volumes.'
}

& docker @arguments
if ($LASTEXITCODE -ne 0) { throw 'Could not stop the local rehearsal stack.' }
