$ErrorActionPreference = 'Stop'
try {
    $workshopNode = (Get-Command node -ErrorAction Stop).Source
    $workshopServer = Join-Path $PSScriptRoot 'server.cjs'
    if (-not (Test-Path -LiteralPath $workshopServer)) { throw 'Missing server.cjs beside the launcher.' }
    Start-Process -FilePath $workshopNode -ArgumentList @('"' + $workshopServer + '"', '--open') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden
} catch {
    Write-Host ("Card Workshop could not start: " + $_.Exception.Message)
    Write-Host 'Install Node.js if it is missing, then run this launcher again.'
    Read-Host 'Press Enter to close'
    exit 1
}
