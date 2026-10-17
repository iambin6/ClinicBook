#Requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path $PSScriptRoot -Parent
$localSettings = Join-Path $backendRoot '.qdrant-local.properties'
if (-not (Test-Path -LiteralPath $localSettings)) {
    $keyBytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
    $localKey = [Convert]::ToHexString($keyBytes)
    [IO.File]::WriteAllText($localSettings, "QDRANT_LOCAL_API_KEY=$localKey`n", [Text.UTF8Encoding]::new($false))
}
& docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop and wait until its engine is running, then retry this script.' }
& docker compose --project-directory $backendRoot --env-file $localSettings -f (Join-Path $backendRoot 'compose.qdrant.yaml') up -d
if ($LASTEXITCODE -ne 0) { throw 'Could not start local Qdrant.' }
Write-Output 'Qdrant REST: http://127.0.0.1:6333; gRPC: 127.0.0.1:6334.'
Write-Output 'Use Spring profile qdrant-local for local backend. Cloud configuration is preserved.'
