# Starts the app in the background. Log: app.log, PID file: .app.pid (PID + process start time)
Set-Location $PSScriptRoot

function Get-AppProcess {
    # Returns the recorded process only if it is still the same one (PID + start time match).
    if (-not (Test-Path .app.pid)) { return $null }
    $lines = @(Get-Content .app.pid)
    if ($lines.Count -lt 2) { return $null }
    $id = 0
    if (-not [int]::TryParse($lines[0], [ref]$id) -or $id -le 4) { return $null }
    $proc = Get-Process -Id $id -ErrorAction SilentlyContinue
    if ($proc -and [math]::Abs($proc.StartTime.ToUniversalTime().Ticks - [long]$lines[1]) -lt 20000000) { return $proc }
    return $null
}

$existing = Get-AppProcess
if ($existing) {
    Write-Host "Already running (PID $($existing.Id)). Use stop.cmd first."
    exit 1
}
if (Test-Path .app.pid) { Remove-Item .app.pid }

$p = Start-Process -FilePath cmd.exe -ArgumentList '/c', 'mvn spring-boot:run > app.log 2>&1' `
    -WindowStyle Hidden -PassThru
@($p.Id, $p.StartTime.ToUniversalTime().Ticks) | Set-Content .app.pid
Write-Host "Starting noname.fm in the background (PID $($p.Id)). Log: app.log"
Write-Host "Open http://localhost:8080/player once the log shows 'Started StreamerApplication'."
