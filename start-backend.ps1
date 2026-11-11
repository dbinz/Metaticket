$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
docker --context desktop-linux info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Linux engine is unavailable. Restart Windows after WSL setup, then start Docker Desktop and wait until the engine is running.'
}
docker --context desktop-linux compose -f compose.local.yml up -d --build
if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed. See Docker Compose output above.' }
docker --context desktop-linux compose -f compose.local.yml ps
Write-Output 'Search backend: http://localhost:48080 (may need time to initialize).'
Write-Output 'Logs: docker --context desktop-linux compose -f compose.local.yml logs -f search'
