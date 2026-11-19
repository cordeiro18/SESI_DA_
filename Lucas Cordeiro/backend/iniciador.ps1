$ErrorActionPreference = "Stop"

$healthUri = "http://127.0.0.1:8081/api/health"
$siteUri = "http://127.0.0.1:8081/login"
$healthResponse = $null
$lastError = $null

try {
    $healthResponse = Invoke-RestMethod -Uri $healthUri -TimeoutSec 2
} catch {
    $lastError = $_.Exception.Message
}

if ($null -ne $healthResponse -and $healthResponse.status -ne "ok") {
    throw "A porta 8081 respondeu, mas nao pertence ao backend DogMania."
}

if ($null -eq $healthResponse) {
    $serverScript = Join-Path $PSScriptRoot "iniciar.ps1"
    $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$serverScript`""
    Start-Process -FilePath "powershell.exe" -ArgumentList $arguments -WorkingDirectory $PSScriptRoot

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $healthResponse = Invoke-RestMethod -Uri $healthUri -TimeoutSec 2
            if ($healthResponse.status -ne "ok") {
                throw "A porta 8081 respondeu, mas nao pertence ao backend DogMania."
            }
            $ready = $true
            break
        } catch {
            $lastError = $_.Exception.Message
            Start-Sleep -Seconds 1
        }
    }

    if (-not $ready) {
        throw "O backend nao ficou pronto em 60 segundos. Ultimo erro: $lastError"
    }
}

Start-Process -FilePath $siteUri
Write-Host "DogMania esta pronto em $siteUri"
