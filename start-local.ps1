$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Test-DockerReady {
    & cmd.exe /d /c 'docker --context desktop-linux info >nul 2>&1'
    return ($LASTEXITCODE -eq 0)
}

if (-not (Test-DockerReady)) {
    $dockerDesktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    $userDockerDesktop = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'
    if (-not (Test-Path -LiteralPath $dockerDesktop)) {
        $dockerDesktop = $userDockerDesktop
    }
    if (-not (Test-Path -LiteralPath $dockerDesktop)) {
        throw 'Docker Desktop was not found. Install or open Docker Desktop, then run npm run dev again.'
    }
    Write-Host 'Opening Docker Desktop. Waiting for the Linux engine...'
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
    $dockerDeadline = (Get-Date).AddMinutes(3)
    while (-not (Test-DockerReady)) {
        if ((Get-Date) -ge $dockerDeadline) {
            throw 'Docker did not become ready within 3 minutes. Open Docker Desktop and check its startup error, then run npm run dev again.'
        }
        Start-Sleep -Seconds 3
    }
}

# Run the backend in a child process so its exit code can be checked reliably.
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'start-core-backend.ps1')
if ($LASTEXITCODE -ne 0) {
    throw 'Backend startup failed. Check the output above and make sure Docker Desktop is running.'
}

Write-Host 'Starting frontend: http://localhost:4200'
try {
    $frontendResponse = Invoke-WebRequest -Uri 'http://127.0.0.1:4200' -UseBasicParsing -TimeoutSec 3
} catch {
    $frontendResponse = $null
}
if ($frontendResponse -and $frontendResponse.StatusCode -eq 200) {
    Write-Host 'Frontend is already running. Open http://localhost:4200 in your browser.'
    exit 0
}
Write-Host 'Press Ctrl+C to stop the frontend. Backend containers keep running.'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'start-home.ps1')
exit $LASTEXITCODE
