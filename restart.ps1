# Restarts the app: stop.ps1, then start.ps1.
Set-Location $PSScriptRoot

& "$PSScriptRoot\stop.ps1"
Start-Sleep -Seconds 1
& "$PSScriptRoot\start.ps1"
