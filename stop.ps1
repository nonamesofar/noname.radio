# Stops the app started by start.cmd (kills the whole process tree).
Set-Location $PSScriptRoot

$stopped = $false

# 1. The process we recorded - only if PID and start time still match (guards against PID reuse).
if (Test-Path .app.pid) {
    $lines = @(Get-Content .app.pid)
    $id = 0
    if ($lines.Count -ge 2 -and [int]::TryParse($lines[0], [ref]$id) -and $id -gt 4) {
        $proc = Get-Process -Id $id -ErrorAction SilentlyContinue
        if ($proc -and [math]::Abs($proc.StartTime.ToUniversalTime().Ticks - [long]$lines[1]) -lt 20000000) {
            & taskkill /PID $id /T /F | Out-Null
            $stopped = $true
        }
    }
    Remove-Item .app.pid
}

# 2. Fallback: a java process still listening on 8080 (e.g. app started by hand). Never kill non-java processes.
$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    $owner = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
    if ($owner -and $owner.ProcessName -match '^javaw?$') {
        & taskkill /PID $owner.Id /T /F | Out-Null
        $stopped = $true
    } elseif ($owner) {
        Write-Host "Port 8080 is held by '$($owner.ProcessName)' (PID $($owner.Id)), which is not java - left alone."
    }
}

if ($stopped) { Write-Host "Stopped." } else { Write-Host "Not running." }
