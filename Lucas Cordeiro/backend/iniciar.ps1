$ErrorActionPreference = "Stop"
$variableName = "CLINICA_CPF_KEY"
$userKey = [Environment]::GetEnvironmentVariable($variableName, "User")
$processKey = [Environment]::GetEnvironmentVariable($variableName, "Process")

if (-not [string]::IsNullOrWhiteSpace($userKey)) {
    if (-not [string]::IsNullOrWhiteSpace($processKey) -and $processKey -ne $userKey) {
        throw "CLINICA_CPF_KEY do processo difere da chave persistida do usuário."
    }
    $key = $userKey
} elseif (-not [string]::IsNullOrWhiteSpace($processKey)) {
    $key = $processKey
    [Environment]::SetEnvironmentVariable($variableName, $key, "User")
} else {
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 32
    $random.GetBytes($bytes)
    $random.Dispose()
    $key = [Convert]::ToBase64String($bytes)
    [Environment]::SetEnvironmentVariable($variableName, $key, "User")
    Write-Host "Chave de proteção do CPF criada no perfil do Windows."
}

try {
    $decodedKey = [Convert]::FromBase64String($key)
} catch {
    throw "CLINICA_CPF_KEY precisa ser uma chave Base64 válida."
}
if ($decodedKey.Length -ne 32) {
    throw "CLINICA_CPF_KEY precisa decodificar para exatamente 32 bytes."
}

$env:CLINICA_CPF_KEY = $key
Set-Location $PSScriptRoot
& (Join-Path $PSScriptRoot "mvnw.cmd") "compile" "exec:java"
exit $LASTEXITCODE
