param([ValidateSet('local','postgres')][string]$Profile = 'local', [switch]$Semantic)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if ($Semantic) {
    if ($Profile -ne 'postgres') { throw 'Semantic search requires -Profile postgres.' }
    $env:SEMANTIC_ENABLED = 'true'
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $installedJdk = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '21' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe')) } |
        Select-Object -First 1
    if (-not $installedJdk) { throw 'Java 21 is required. Set JAVA_HOME to your JDK folder.' }
    $env:JAVA_HOME = $installedJdk.FullName
}
if ($Profile -eq 'postgres') {
    if (Test-Path -LiteralPath '.env') {
        foreach ($line in Get-Content -LiteralPath '.env') {
            if ($line -match '^\s*(POSTGRES_USER|POSTGRES_PASSWORD)=(.*)$') {
                [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
            }
        }
    }
    if (-not $env:POSTGRES_PASSWORD) { throw 'Set POSTGRES_PASSWORD in .env first (see .env.example).' }
    docker compose up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop and retry.' }
}
Write-Host 'Document assistant: http://localhost:8080 (Ctrl+C to stop)'
& .\gradlew.bat bootRun "--args=--spring.profiles.active=$Profile"
exit $LASTEXITCODE
