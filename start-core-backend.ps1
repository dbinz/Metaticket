param([switch]$Build)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
node .\prepare-core-local.mjs
if ($LASTEXITCODE -ne 0) { throw 'Failed to prepare local service configuration.' }
docker --context desktop-linux info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop and wait until the Linux engine is running.' }
$existingImage = docker --context desktop-linux image ls -q metaticket-core-local
$imageMissing = -not $existingImage
if ($Build -or $imageMissing) {
    docker --context desktop-linux compose -f compose.local.yml -f compose.core.local.json build service.show
    if ($LASTEXITCODE -ne 0) { throw 'Core backend build failed.' }
}
docker --context desktop-linux compose -f compose.local.yml -f compose.core.local.json up -d mongo-init redis-init kafka
if ($LASTEXITCODE -ne 0) { throw 'Local infrastructure startup failed.' }
docker --context desktop-linux compose -f compose.local.yml -f compose.core.local.json wait mongo-init redis-init
if ($LASTEXITCODE -ne 0) { throw 'Local database initialization failed.' }
Get-Content .\bootstrap-core-mongo.js -Raw | docker --context desktop-linux compose -f compose.local.yml -f compose.core.local.json exec -T mongo mongosh --port 47017 --quiet
if ($LASTEXITCODE -ne 0) { throw 'Local database bootstrap failed.' }
docker --context desktop-linux compose -f compose.local.yml -f compose.core.local.json up -d --no-build
if ($LASTEXITCODE -ne 0) { throw 'Core backend startup failed. Check Docker Compose logs.' }
docker --context desktop-linux compose -f compose.local.yml -f compose.core.local.json ps
Write-Output 'Core API gateway: http://localhost:8081. Services may need time to initialize.'
