#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$Image = 'clinic-booking:render-startup-fix',
    [ValidateRange(0.1, 4)][decimal]$Cpus = 0.1,
    [ValidateRange(1024, 65535)][int]$Port = 11400,
    [ValidateRange(30, 600)][int]$StartupTimeoutSeconds = 300
)
$ErrorActionPreference = 'Stop'
$project = "clinic-render-startup-smoke-$PID"
$composePath = Join-Path ([IO.Path]::GetTempPath()) "clinic-render-smoke-$PID.compose.json"
$testPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$spec = @{
    services = @{
        database = @{
            image = 'mysql:8.4'
            mem_limit = '512m'
            environment = @{ MYSQL_ROOT_PASSWORD = $testPassword; MYSQL_DATABASE = 'clinic_render_smoke' }
            command = @('--innodb-buffer-pool-size=64M', '--performance-schema=OFF')
            healthcheck = @{
                test = @('CMD', 'mysqladmin', 'ping', '-h', '127.0.0.1', '--silent')
                interval = '3s'; timeout = '3s'; retries = 40; start_period = '10s'
            }
        }
        api = @{
            image = $Image
            mem_limit = '512m'
            memswap_limit = '512m'
            cpus = $Cpus
            depends_on = @{ database = @{ condition = 'service_healthy' } }
            ports = @("127.0.0.1:${Port}:10000")
            environment = @{
                PORT = '10000'
                SPRING_CONFIG_IMPORT = 'optional:classpath:smoke-no-secrets.properties'
                SPRING_DATASOURCE_URL = 'jdbc:mysql://database:3306/clinic_render_smoke?allowPublicKeyRetrieval=true&useSSL=false'
                SPRING_DATASOURCE_USERNAME = 'root'
                SPRING_DATASOURCE_PASSWORD = $testPassword
                GOOGLE_CLIENT_ID = 'smoke-test.apps.googleusercontent.com'
                GOOGLE_CLIENT_SECRET = $testPassword
                FACEBOOK_CLIENT_ID = 'smoke-test'
                FACEBOOK_CLIENT_SECRET = $testPassword
                MAIL_USERNAME = 'smoke@example.invalid'
                MAIL_PASSWORD = $testPassword
                SPRING_MAIL_HOST = 'unavailable.invalid'
                JWT_SECRET = $testPassword
                GEMINI_API_KEY = 'smoke-test-only'
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
    & docker @composeArgs up -d --wait --wait-timeout 120 database
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the disposable local database.' }
    $timer = [Diagnostics.Stopwatch]::StartNew()
    & docker @composeArgs up -d api
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the backend smoke container.' }
    $apiId = (& docker @composeArgs ps -q api).Trim()
    $peakMemoryPercent = 0.0
    $listenerSeconds = $null
    $ready = $false
    while ($timer.Elapsed.TotalSeconds -lt $StartupTimeoutSeconds) {
        $state = (& docker inspect --format '{{json .State}}' $apiId) | ConvertFrom-Json
        if (-not $state.Running) { throw "Backend exited: code=$($state.ExitCode), OOMKilled=$($state.OOMKilled)." }
        $memoryPercent = (& docker stats --no-stream --format '{{.MemPerc}}' $apiId).TrimEnd('%')
        $peakMemoryPercent = [Math]::Max($peakMemoryPercent, [double]::Parse($memoryPercent, [Globalization.CultureInfo]::InvariantCulture))
        if ($null -eq $listenerSeconds) {
            $sockets = (& docker exec $apiId cat /proc/net/tcp /proc/net/tcp6) -join "`n"
            if ($sockets -match ':2710\s+[0-9A-Fa-f:]+\s+0A\s') {
                $listenerSeconds = $timer.Elapsed.TotalSeconds
            }
        }
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/specialties/all" -TimeoutSec 3 -SkipHttpErrorCheck
            if ([int]$response.StatusCode -eq 200) { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw "Backend did not serve a database-backed API within $StartupTimeoutSeconds seconds." }
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 3
    if ($health.status -ne 'UP') { throw 'Readiness check did not return UP after startup.' }
    Write-Output ("PASS: /health and /specialties/all=200, listener={0:N1}s, ready={1:N1}s, peak sampled RAM={2:N1}MiB, limit=512MiB, CPUs={3}." -f $listenerSeconds, $timer.Elapsed.TotalSeconds, ($peakMemoryPercent * 512 / 100), $Cpus)
    $success = $true
} finally {
    $logs = (& docker @composeArgs logs --no-color --tail 80 api) -join [Environment]::NewLine
    if ($success) {
        $logs -split '\r?\n' | Where-Object { $_ -match 'Tomcat initialized|Tomcat started|Started ClinicBookingApplication|active profile' }
    } else {
        Write-Output ($logs.Replace($testPassword, '[TEST_CREDENTIAL]'))
    }
    # Only this script's disposable Compose project and synthetic data are removed.
    & docker @composeArgs down --volumes
    Remove-Item -LiteralPath $composePath -Force
}
