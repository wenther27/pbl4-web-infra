param(
    [string[]]$Services = @('web-1', 'web-2', 'web-3'),
    [switch]$Build,
    [string]$AdminUrl = 'http://localhost:8090',
    [string]$Token = '',
    [int]$HealthPortBase = 8080,      # web-N -> http://localhost:(8080+N)/actuator/health
    [int]$DrainTimeoutSec = 30,
    [int]$StartTimeoutSec = 240
)

$ErrorActionPreference = 'Stop'

if (-not $Token) { $Token = $env:LB_ADMIN_TOKEN }
if (-not $Token -and (Test-Path '.env')) {
    $line = Get-Content '.env' | Where-Object { $_ -match '^\s*LB_ADMIN_TOKEN\s*=' } | Select-Object -First 1
    if ($line) { $Token = ($line -split '=', 2)[1].Trim() }
}
if (-not $Token) { $Token = 'dev-admin-token-doi-di' }
$Headers = @{ 'X-Admin-Token' = $Token; 'X-Admin-User' = 'rolling-deploy' }

function Log([string]$msg, [string]$color = 'Gray') {
    Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $msg) -ForegroundColor $color
}

function Get-Backend([string]$name) {
    $all = (Invoke-RestMethod -Uri "$AdminUrl/admin/backends" -Headers $Headers -TimeoutSec 5).backends
    return $all | Where-Object { $_.name -eq $name } | Select-Object -First 1
}

function Invoke-Lb([string]$name, [string]$action) {
    return Invoke-RestMethod -Method Post -Uri "$AdminUrl/admin/backends/$name/$action" -Headers $Headers -TimeoutSec 5
}

function Test-Health([string]$name) {
    $n = [int]($name -replace '\D', '')
    try {
        $r = Invoke-RestMethod -Uri ("http://localhost:{0}/actuator/health" -f ($HealthPortBase + $n)) -TimeoutSec 2
        return $r.status -eq 'UP'
    } catch {
        return $false
    }
}

try {
    $before = (Invoke-RestMethod -Uri "$AdminUrl/admin/backends" -Headers $Headers -TimeoutSec 5).backends
} catch {
    Log "Khong goi duoc API quan tri LB ($AdminUrl). LB da chay chua? Token dung chua?" Red
    exit 1
}
$down = @($before | Where-Object { -not $_.up })
if ($down.Count -gt 0) {
    Log ("Co backend dang DOWN: " + (($down | ForEach-Object { $_.name }) -join ', ') + " -> khong rolling deploy luc nay") Red
    exit 1
}
Log ("Bat dau rolling deploy: " + ($Services -join ' -> ')) Cyan

$total = [Diagnostics.Stopwatch]::StartNew()

if ($Build) {
    Log "Build image backend moi (dung chung cho web-1/2/3)..." Cyan
    docker compose build $Services[0]
    if ($LASTEXITCODE -ne 0) { Log "Build loi -> dung lai, chua dung vao backend nao" Red; exit 1 }
}

$i = 0
foreach ($svc in $Services) {
    $i++
    $sw = [Diagnostics.Stopwatch]::StartNew()
    Log ("===== [{0}/{1}] {2} =====" -f $i, $Services.Count, $svc) Yellow
    try {
        try {
            $null = Invoke-Lb $svc 'drain'
        } catch {
            Log ("LB tu choi drain $svc" + ": " + $_.ErrorDetails.Message) Red
            exit 1
        }
        Log "1. DRAIN: LB ngung gui request moi vao $svc"

        $deadline = (Get-Date).AddSeconds($DrainTimeoutSec)
        while ($true) {
            $b = Get-Backend $svc
            if ($b.inFlight -eq 0) { break }
            if ((Get-Date) -gt $deadline) { Log ("   Qua {0}s van con {1} request, tiep tuc" -f $DrainTimeoutSec, $b.inFlight) Red; break }
            Start-Sleep -Milliseconds 300
        }
        Log "2. Request dang xu ly tren $svc = 0"

        if ($Build) {
            docker compose up -d --no-deps --force-recreate $svc
        } else {
            docker compose restart $svc
        }
        if ($LASTEXITCODE -ne 0) { throw "docker compose loi khi khoi dong lai $svc" }
        Log "3. Da khoi dong lai $svc, cho no san sang..."

        Start-Sleep -Seconds 3
        $deadline = (Get-Date).AddSeconds($StartTimeoutSec)
        while (-not (Test-Health $svc)) {
            if ((Get-Date) -gt $deadline) { throw "$svc khong len sau $StartTimeoutSec giay" }
            Start-Sleep -Seconds 2
        }
        while (-not (Get-Backend $svc).up) {
            if ((Get-Date) -gt $deadline) { throw "LB chua thay $svc UP sau $StartTimeoutSec giay" }
            Start-Sleep -Seconds 1
        }
        Log "4. $svc da UP (health + LB)"
    } finally {
        try { $null = Invoke-Lb $svc 'undrain' } catch { }
        Log ("5. UNDRAIN: {0} nhan request tro lai ({1:N0}s)" -f $svc, $sw.Elapsed.TotalSeconds) Green
    }
}

Log ("Xong rolling deploy {0} backend trong {1:N0}s" -f $Services.Count, $total.Elapsed.TotalSeconds) Cyan
(Invoke-RestMethod -Uri "$AdminUrl/admin/backends" -Headers $Headers).backends |
    Format-Table name, up, draining, inFlight -AutoSize | Out-String | Write-Host
