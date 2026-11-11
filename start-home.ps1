$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot 'metaticket-home-master/metaticket-home-master')
npm.cmd run start:local
exit $LASTEXITCODE
