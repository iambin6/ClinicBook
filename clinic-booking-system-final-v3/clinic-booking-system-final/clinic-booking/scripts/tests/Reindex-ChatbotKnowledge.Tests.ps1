#Requires -Version 7.0
# Offline regression checks; every HTTP request is intercepted by the function below.
$ErrorActionPreference = 'Stop'
$migrationScript = Join-Path $PSScriptRoot '../Reindex-ChatbotKnowledge.ps1'
$fixture = [IO.Path]::GetTempFileName()
$knowledgeFixture = [IO.Path]::GetTempFileName()
$global:ChatbotReindexTestRequests = [Collections.Generic.List[object]]::new()
$global:ChatbotReindexTestRejectGemini = $false
$global:ChatbotReindexTestExpectedKey = 'fixture-key-not-a-real-credential'
$script:originalGeminiKey = [Environment]::GetEnvironmentVariable('GEMINI_API_KEY')

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-WebRequest {
    param($Uri, $Method, $Headers, $TimeoutSec, $SkipHttpErrorCheck, $Body, $ContentType)
    $decodedBody = if ($Body) { [Text.Encoding]::UTF8.GetString($Body) | ConvertFrom-Json -AsHashtable } else { $null }
    $global:ChatbotReindexTestRequests.Add(@{ Uri = $Uri; Method = $Method; Body = $decodedBody })
    $status = 200
    if ($Uri -like 'https://generativelanguage.googleapis.com/*') {
        Assert-True ($Headers['x-goog-api-key'] -eq $global:ChatbotReindexTestExpectedKey) 'Embedding did not send the API key in a header.'
        Assert-True ($decodedBody.outputDimensionality -eq 3) 'Wrong embedding size.'
        if ($global:ChatbotReindexTestRejectGemini) {
            $status = 403
            $content = @{ error = @{ message = "Private key $global:ChatbotReindexTestExpectedKey"; details = @(@{ reason = 'CONSUMER_SUSPENDED' }) } }
        } else { $content = @{ embedding = @{ values = @(0.1, 0.2, 0.3) } } }
    } elseif ($Uri -eq 'http://127.0.0.1:9999/collections/old' -and $Method -eq 'GET') {
        $content = @{ result = @{ points_count = 2 } }
    } elseif ($Uri -eq 'http://127.0.0.1:9999/collections/new' -and $Method -eq 'GET') {
        $status = 404
        $content = @{ status = @{ error = 'missing' } }
    } elseif ($Uri -like '*/old/points/scroll') {
        $content = @{ result = @{ points = @(
                    @{ id = '1'; payload = @{ text = 'Clinic knowledge'; category = 'clinic' } },
                    @{ id = '2'; payload = @{ category = 'no-text' } }
                ); next_page_offset = $null } }
    } elseif ($Method -eq 'PUT' -and $Uri -like '*/collections/new*') {
        $content = @{ result = @{ status = 'completed' } }
    } else { throw "Unexpected test request: $Method $Uri" }
    return [pscustomobject]@{ StatusCode = $status; Content = ($content | ConvertTo-Json -Depth 20 -Compress) }
}

try {
    # Ensure an unrelated developer environment never overrides this dummy fixture key.
    [Environment]::SetEnvironmentVariable('GEMINI_API_KEY', $global:ChatbotReindexTestExpectedKey)
    Set-Content -LiteralPath $fixture -Value "GEMINI_API_KEY=$global:ChatbotReindexTestExpectedKey"
    $arguments = @{
        ConfigPath = $fixture; SourceCollection = 'old'; TargetCollection = 'new'
        EmbeddingModel = 'fixture-embedding'; Dimensions = 3; QdrantUrl = 'http://127.0.0.1:9999'
    }
    $null = & $migrationScript @arguments
    Assert-True ($global:ChatbotReindexTestRequests.Count -eq 2) 'Preview must only read source and target metadata.'
    Assert-True (@($global:ChatbotReindexTestRequests | Where-Object { $_.Method -ne 'GET' }).Count -eq 0) 'Preview wrote data.'

    $global:ChatbotReindexTestRequests.Clear()
    $global:ChatbotReindexTestRejectGemini = $true
    $failure = $null
    try { $null = & $migrationScript @arguments -Apply } catch { $failure = $_.Exception.Message }
    Assert-True ($failure -like '*CONSUMER_SUSPENDED*') 'Suspended key did not fail preflight.'
    Assert-True (-not $failure.Contains($global:ChatbotReindexTestExpectedKey)) 'Migration failure leaked the API key.'
    Assert-True (@($global:ChatbotReindexTestRequests | Where-Object { $_.Method -eq 'PUT' }).Count -eq 0) 'Failed preflight wrote data.'

    $global:ChatbotReindexTestRequests.Clear()
    $global:ChatbotReindexTestRejectGemini = $false
    $null = & $migrationScript @arguments -Apply
    $writes = @($global:ChatbotReindexTestRequests | Where-Object { $_.Method -eq 'PUT' })
    Assert-True ($writes.Count -eq 2) 'Expected collection creation and one points upload.'
    Assert-True ($writes[0].Body.vectors.size -eq 3) 'Created collection has the wrong dimensions.'
    $points = @($writes[1].Body.points)
    Assert-True ($points.Count -eq 1) 'Points without a text payload should be skipped.'
    Assert-True ($points[0].payload._embedding_model -eq 'fixture-embedding') 'Missing model metadata.'
    Assert-True ($points[0].payload.category -eq 'clinic') 'Original payload was not preserved.'
    Assert-True ($points[0].id -eq '1' -and $points[0].vector.Count -eq 3) 'Wrong ID or vector.'
    Assert-True (@($writes | Where-Object { $_.Uri -like '*/old*' }).Count -eq 0) 'Source collection was modified.'

    $global:ChatbotReindexTestRequests.Clear()
    Set-Content -LiteralPath $knowledgeFixture -Value '[{"id":3,"payload":{"text":"New FAQ","source":"fixture"}}]'
    $null = & $migrationScript @arguments -KnowledgeFile $knowledgeFixture -Apply
    $sourceReads = @($global:ChatbotReindexTestRequests | Where-Object { $_.Uri -like '*/old*' })
    Assert-True ($sourceReads.Count -eq 0) 'Knowledge-file initialization must not depend on a missing cloud source.'
    $upload = @($global:ChatbotReindexTestRequests | Where-Object { $_.Method -eq 'PUT' })[1]
    Assert-True ($upload.Body.points[0].id -eq 3) 'Knowledge-file initialization did not preserve the point ID.'
    Write-Output 'PASS: preview, suspended-key preflight, key redaction, reindex and knowledge-file initialization.'
} finally {
    [Environment]::SetEnvironmentVariable('GEMINI_API_KEY', $script:originalGeminiKey)
    Remove-Item -LiteralPath $fixture -Force
    Remove-Item -LiteralPath $knowledgeFixture -Force
    Remove-Variable -Name ChatbotReindexTestRequests,ChatbotReindexTestRejectGemini,ChatbotReindexTestExpectedKey -Scope Global
}
