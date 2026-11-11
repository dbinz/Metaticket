$ErrorActionPreference = 'Stop'
$logPath = Join-Path $PSScriptRoot 'install-wsl.log'
$statusPath = Join-Path $PSScriptRoot 'install-wsl-status.json'
Start-Transcript -Path $logPath -Append
try {
    $principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run this script as Administrator.'
    }
    $restartNeeded = $false
    foreach ($feature in @('Microsoft-Windows-Subsystem-Linux', 'VirtualMachinePlatform')) {
        $state = Get-WindowsOptionalFeature -Online -FeatureName $feature
        Write-Output "$feature : $($state.State)"
        if ($state.State -ne 'Enabled') {
            $result = Enable-WindowsOptionalFeature -Online -FeatureName $feature -All -NoRestart
            $restartNeeded = $restartNeeded -or $result.RestartNeeded
        }
    }
    & wsl.exe --install --no-distribution --web-download
    $installExitCode = $LASTEXITCODE
    if ($installExitCode -notin @(0, 3010)) {
        throw "WSL installer exit code: $installExitCode. See install-wsl.log."
    }
    $restartNeeded = $restartNeeded -or ($installExitCode -eq 3010)
    & wsl.exe --set-default-version 2
    $defaultVersionExitCode = $LASTEXITCODE
    @{status='installed'; restartNeeded=$restartNeeded; defaultVersionExitCode=$defaultVersionExitCode} |
        ConvertTo-Json | Set-Content -LiteralPath $statusPath -Encoding UTF8
} catch {
    @{status='failed'; error=$_.Exception.Message} | ConvertTo-Json |
        Set-Content -LiteralPath $statusPath -Encoding UTF8
    Write-Output $_
    exit 1
} finally {
    Stop-Transcript
}
