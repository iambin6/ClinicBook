#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$Image = 'clinic-booking:render-smoke',
    [ValidateRange(1024, 65535)][int]$Port = 11000
)
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path $PSScriptRoot -Parent
$outputDirectory = Join-Path $backendRoot 'target'
[void][IO.Directory]::CreateDirectory($outputDirectory)
$composePath = Join-Path $outputDirectory 'render-smoke.compose.json'
$project = 'clinic-render-smoke'
# Only disposable credentials and a new, isolated database are used by this test.
$testPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$spec = @{
    services = @{
        database = @{
            image = 'mysql:8.4'
            mem_limit = '512m'
            environment = @{ MYSQL_ROOT_PASSWORD = $testPassword; MYSQL_DATABASE = 'clinicbookingdb' }
            command = @('--innodb-buffer-pool-size=64M', '--performance-schema=OFF')
            healthcheck = @{
                test = @('CMD', 'mysqladmin', 'ping', '-h', '127.0.0.1', '--silent')
                interval = '3s'; timeout = '3s'; retries = 40; start_period = '15s'
            }
        }
        api = @{
            image = $Image
            mem_limit = '512m'
            cpus = 1
            depends_on = @{ database = @{ condition = 'service_healthy' } }
            ports = @("127.0.0.1:${Port}:10000")
            environment = @{
                PORT = '10000'
                SPRING_PROFILES_ACTIVE = 'render'
                SPRING_DATASOURCE_URL = 'jdbc:mysql://database:3306/clinicbookingdb?allowPublicKeyRetrieval=true&useSSL=false'
                SPRING_DATASOURCE_USERNAME = 'root'
                SPRING_DATASOURCE_PASSWORD = $testPassword
                GOOGLE_CLIENT_ID = 'smoke-test.apps.googleusercontent.com'
                GOOGLE_CLIENT_SECRET = $testPassword
                FACEBOOK_CLIENT_ID = 'smoke-test'
                FACEBOOK_CLIENT_SECRET = $testPassword
                MAIL_USERNAME = 'smoke@example.invalid'
                MAIL_PASSWORD = $testPassword
                JWT_SECRET = $testPassword
                # A broken optional dependency must not block the HTTP listener.
                QDRANT_ENABLED = 'true'
                QDRANT_HOST = 'unavailable.invalid'
                QDRANT_API_KEY = 'smoke-test-only'
            }
        }
    }
}
[IO.File]::WriteAllText($composePath, ($spec | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
$composeArgs = @('compose', '-p', $project, '-f', $composePath)
$success = $false
try {
    & docker @composeArgs up -d --wait --wait-timeout 180
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the isolated Render smoke environment.' }
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $ready = $false
    while ($timer.Elapsed.TotalSeconds -lt 120) {
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 3 -SkipHttpErrorCheck
            if ([int]$response.StatusCode -eq 200) { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'The backend did not become ready within 120 seconds.' }
    $body = $response.Content | ConvertFrom-Json
    if ($body.status -ne 'UP') { throw 'The health endpoint returned an unexpected response.' }
    Write-Output 'PASS: HTTP /health=200 with PORT=10000, 512 MB RAM, 1 CPU and unreachable Qdrant.'
    $apiId = (& docker @composeArgs ps -q api).Trim()
    & docker stats --no-stream --format 'Memory: {{.MemUsage}}; CPU: {{.CPUPerc}}' $apiId
    $logs = & docker @composeArgs logs --no-color api
    $logs | Where-Object { $_ -match 'Tomcat started|Started ClinicBookingApplication|active profile' }
    $success = $true
} finally {
    if (-not $success) {
        $logs = (& docker @composeArgs logs --no-color --tail 80 api) -join [Environment]::NewLine
        Write-Output ($logs.Replace($testPassword, '[TEST_CREDENTIAL]'))
    }
    # This project was created above and contains only synthetic smoke-test data.
    & docker @composeArgs down --volumes
    Remove-Item -LiteralPath $composePath -Force
}
