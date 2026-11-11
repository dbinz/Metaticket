$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
& .\start-core-backend.ps1
if ($LASTEXITCODE -ne 0) { throw 'Core backend startup failed.' }
docker --context desktop-linux compose -f compose.local.yml -f compose.core.local.json -f compose.web.local.yml build web
if ($LASTEXITCODE -ne 0) { throw 'Frontend image build failed.' }
docker --context desktop-linux compose -f compose.local.yml -f compose.core.local.json -f compose.web.local.yml up -d --no-build web
if ($LASTEXITCODE -ne 0) { throw 'Frontend container startup failed.' }
Write-Output 'MetaTicket Docker website: http://localhost:4300'
