$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
docker --context desktop-linux compose -f compose.local.yml -f compose.core.local.json -f compose.web.local.yml down
exit $LASTEXITCODE
