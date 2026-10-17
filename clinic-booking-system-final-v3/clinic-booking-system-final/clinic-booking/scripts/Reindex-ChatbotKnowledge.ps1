#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot '../src/main/resources/application-secret.properties'),
    [string]$SourceCollection = 'nano_knowledge_base',
    [string]$TargetCollection,
    [string]$EmbeddingModel,
    [ValidateRange(1, 3072)][int]$Dimensions = 768,
    [string]$QdrantUrl,
    [string]$KnowledgeFile,
    [switch]$Local,
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
$settings = @{}
if (Test-Path -LiteralPath $ConfigPath) {
    foreach ($line in Get-Content -LiteralPath $ConfigPath) {
        if ($line -match '^\s*([^#!\s][^=]*?)\s*=\s*(.*)$') {
            $settings[$matches[1]] = $matches[2].Trim()
        }
    }
}

function Get-Setting([string]$Name, [string]$Default = '') {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
    if ($settings.ContainsKey($Name)) { return $settings[$Name] }
    return $Default
}

$geminiKey = Get-Setting 'GEMINI_API_KEY'
$qdrantKey = Get-Setting 'QDRANT_API_KEY'
if (-not $EmbeddingModel) { $EmbeddingModel = Get-Setting 'GEMINI_EMBEDDING_MODEL' 'gemini-embedding-2' }
if (-not $TargetCollection) {
    $TargetCollection = Get-Setting 'QDRANT_COLLECTION' 'nano_knowledge_base_gemini_embedding_2_768'
}
if (-not $KnowledgeFile -and $SourceCollection -eq $TargetCollection) {
    throw 'Source and target must be different collections.'
}
if ($Local) {
    if ($QdrantUrl -and ([Uri]$QdrantUrl).Host -notin @('localhost', '127.0.0.1', '[::1]')) {
        throw '-Local only permits a loopback Qdrant URL.'
    }
    if (-not $QdrantUrl) { $QdrantUrl = 'http://127.0.0.1:6333' }
    $localConfig = Join-Path $PSScriptRoot '../.qdrant-local.properties'
    $localKey = ''
    if (Test-Path -LiteralPath $localConfig) {
        foreach ($line in Get-Content -LiteralPath $localConfig) {
            if ($line -match '^QDRANT_LOCAL_API_KEY=(.+)$') { $localKey = $matches[1].Trim() }
        }
    }
    $qdrantKey = Get-Setting 'QDRANT_LOCAL_API_KEY' $localKey
    if (-not $qdrantKey) { throw 'Run Start-LocalQdrant.ps1 to create local Qdrant credentials first.' }
}
if (-not $QdrantUrl) {
    $qdrantHost = Get-Setting 'QDRANT_HOST'
    if (-not $qdrantHost) { throw 'QDRANT_HOST or -QdrantUrl is required.' }
    # Qdrant Cloud exposes REST on 6333; do not use the gRPC port 6334 here.
    $tls = (Get-Setting 'QDRANT_TLS' 'true') -eq 'true'
    $scheme = if ($tls) { 'https' } else { 'http' }
    $QdrantUrl = '{0}://{1}:6333' -f $scheme, $qdrantHost
}
$QdrantUrl = $QdrantUrl.TrimEnd('/')
$qdrantHeaders = @{}
if ($qdrantKey) { $qdrantHeaders['api-key'] = $qdrantKey }
$geminiHeaders = @{ 'x-goog-api-key' = $geminiKey }
$sourcePath = '/collections/' + [Uri]::EscapeDataString($SourceCollection)
$targetPath = '/collections/' + [Uri]::EscapeDataString($TargetCollection)

function Invoke-JsonApi([string]$Uri, [string]$Method, [hashtable]$Headers,
                        $Body = $null, [switch]$AllowMissing) {
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $arguments = @{
            Uri = $Uri; Method = $Method; Headers = $Headers
            TimeoutSec = 30; SkipHttpErrorCheck = $true
        }
        if ($null -ne $Body) {
            $arguments.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 30 -Compress))
            $arguments.ContentType = 'application/json; charset=utf-8'
        }
        try { $response = Invoke-WebRequest @arguments }
        catch {
            $failureType = $_.Exception.GetType().Name
            throw "Network request failed ($failureType); check service connectivity. Credentials were not logged."
        }
        $status = [int]$response.StatusCode
        if ($AllowMissing -and $status -eq 404) { return $null }
        if ($status -ge 200 -and $status -lt 300) {
            return ($response.Content | ConvertFrom-Json -AsHashtable)
        }
        $reason = 'UNKNOWN'
        try {
            $errorBody = ($response.Content | ConvertFrom-Json -AsHashtable).error
            foreach ($detail in $errorBody.details) {
                if ($detail.reason -cmatch '^[A-Z][A-Z0-9_]{0,63}$') { $reason = $detail.reason; break }
            }
        } catch { }
        if ($attempt -lt 3 -and ($status -in 408, 429 -or $status -ge 500)) {
            Start-Sleep -Milliseconds (500 * [Math]::Pow(2, $attempt - 1))
            continue
        }
        throw "Service request failed: HTTP $status, reason=$reason. Response body omitted to protect credentials."
    }
}

function Get-Embedding([string]$Text) {
    $response = Invoke-JsonApi -Uri "https://generativelanguage.googleapis.com/v1beta/models/${EmbeddingModel}:embedContent" `
        -Method POST -Headers $geminiHeaders -Body @{
            content = @{ parts = @(@{ text = $Text }) }
            outputDimensionality = $Dimensions
        }
    $vector = @($response.embedding.values)
    if ($vector.Count -ne $Dimensions) { throw 'Embedding dimensions do not match the requested dimensions.' }
    return ,$vector
}

if ($KnowledgeFile) {
    $knowledgePoints = @(Get-Content -LiteralPath $KnowledgeFile -Raw | ConvertFrom-Json -AsHashtable)
    if ($knowledgePoints.Count -eq 0) { throw 'Knowledge file contains no points.' }
    foreach ($point in $knowledgePoints) {
        if ($null -eq $point.id -or $point.payload.text -isnot [string] -or
            [string]::IsNullOrWhiteSpace($point.payload.text)) {
            throw 'Each knowledge point must have an id and a non-empty payload.text.'
        }
    }
    $source = @{ result = @{ points_count = $knowledgePoints.Count } }
    $sourceLabel = 'local knowledge file'
} else {
    $source = Invoke-JsonApi -Uri "$QdrantUrl$sourcePath" -Method GET -Headers $qdrantHeaders
    $sourceLabel = $SourceCollection
}
$target = Invoke-JsonApi -Uri "$QdrantUrl$targetPath" -Method GET -Headers $qdrantHeaders -AllowMissing
if ($null -ne $target) {
    if ($target.result.config.params.vectors.size -ne $Dimensions -or
        $target.result.config.params.vectors.distance -ne 'Cosine') {
        throw 'Target collection has an incompatible vector configuration. Choose a new collection.'
    }
    if ($target.result.points_count -gt 0) {
        $sample = Invoke-JsonApi -Uri "$QdrantUrl$targetPath/points/scroll" -Method POST -Headers $qdrantHeaders `
            -Body @{ limit = 1; with_payload = $true; with_vector = $false }
        if ($sample.result.points[0].payload._embedding_model -ne $EmbeddingModel) {
            throw 'Existing target was indexed with another or unknown model. Choose a new collection.'
        }
    }
}
Write-Output "Source: $sourceLabel ($($source.result.points_count) points). Target: $TargetCollection."
if (-not $Apply) {
    Write-Output 'Preview only. Use -Apply to create/resume the target and regenerate vectors. Source is preserved.'
    exit 0
}
if ([string]::IsNullOrWhiteSpace($geminiKey)) { throw 'GEMINI_API_KEY is required for -Apply.' }
# Validate Gemini before writing anything to Qdrant.
$null = Get-Embedding 'Connection test'
if ($null -eq $target) {
    $null = Invoke-JsonApi -Uri "$QdrantUrl$targetPath" -Method PUT -Headers $qdrantHeaders `
        -Body @{ vectors = @{ size = $Dimensions; distance = 'Cosine' } }
}

$offset = $null
$indexed = 0
$skipped = 0
do {
    $scrollBody = @{ limit = 16; with_payload = $true; with_vector = $false }
    if ($null -ne $offset) { $scrollBody.offset = $offset }
    if ($KnowledgeFile) {
        $page = @{ result = @{ points = $knowledgePoints; next_page_offset = $null } }
    } else {
        $page = Invoke-JsonApi -Uri "$QdrantUrl$sourcePath/points/scroll" -Method POST `
            -Headers $qdrantHeaders -Body $scrollBody
    }
    $points = @()
    foreach ($point in $page.result.points) {
        $text = $point.payload.text
        if ($text -isnot [string] -or [string]::IsNullOrWhiteSpace($text)) { $skipped++; continue }
        $vector = Get-Embedding $text
        $point.payload._embedding_model = $EmbeddingModel
        $point.payload._embedding_dimensions = $Dimensions
        $points += @{ id = $point.id; vector = $vector; payload = $point.payload }
    }
    if ($points.Count -gt 0) {
        $null = Invoke-JsonApi -Uri "$QdrantUrl$targetPath/points?wait=true" -Method PUT `
            -Headers $qdrantHeaders -Body @{ points = $points }
        $indexed += $points.Count
        Write-Output "Indexed $indexed points; skipped $skipped points without text payload."
    }
    $offset = $page.result.next_page_offset
} while ($null -ne $offset)
Write-Output "Completed: $indexed indexed, $skipped skipped. Set QDRANT_COLLECTION=$TargetCollection and restart backend."
