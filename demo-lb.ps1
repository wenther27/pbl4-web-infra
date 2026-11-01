# ============================================================
# CineHub - Demo Load Balancer + Phat hien bat thuong (theo PBL4_ThuTuChayKiemThu.pdf)
# Chay:  powershell -ExecutionPolicy Bypass -File .\demo-lb.ps1
#   -NoPause      : khong dung giua cac buoc
#   -SkipManual   : bo qua cac buoc can thao tac tay tren web (R7, R3, R4)
# Yeu cau: da chay "docker compose up -d" va ELK; cac backend da UP.
# Script chi gui vai request kiem tra chuc nang; cac buoc R7, R3, R4 do NGUOI thao tac tren web,
# script chi cho va doc ket qua tu Elasticsearch.
# ============================================================
param([switch]$NoPause, [switch]$SkipManual, [int]$ManualTimeout = 150)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Set-Location $PSScriptRoot

$LbUrl    = "http://localhost:8080"
$EsUrl    = "http://localhost:9200"
$Lb       = "cinehub-infrastructure-load-balancer-1"
$Detector = "cinehub-infrastructure-anomaly-detector-1"
$Ports    = @{ "web-1" = 8081; "web-2" = 8082; "web-3" = 8083 }
$TagIp    = "192.0.2.10"     # IP gia (dai danh cho tai lieu) de nhan ra request cua script trong log
$script:Results = @()

Add-Type -AssemblyName System.Net.Http
$script:Http = New-Object System.Net.Http.HttpClient
$script:Http.Timeout = [TimeSpan]::FromSeconds(20)

# ------------------------------------------------------------
# Ham hien thi
# ------------------------------------------------------------
function Title($text) {
    Write-Host ""
    Write-Host ("=" * 70) -ForegroundColor DarkGray
    Write-Host " $text" -ForegroundColor Cyan
    Write-Host ("=" * 70) -ForegroundColor DarkGray
}
function Info($text)    { Write-Host "  $text" }
function Explain($text) { Write-Host "  Y nghia: $text" -ForegroundColor DarkCyan }
function Todo($text)    { Write-Host "  >> BAN LAM: $text" -ForegroundColor Magenta }
function Check($code, $name, [bool]$ok, $detail = "") {
    $script:Results += [pscustomobject]@{ Ma = $code; Buoc = $name; KetQua = $(if ($ok) { "DAT" } else { "LOI" }); ChiTiet = $detail }
    if ($ok) { Write-Host "  [DAT] $code $name  $detail" -ForegroundColor Green }
    else     { Write-Host "  [LOI] $code $name  $detail" -ForegroundColor Red }
}
function Skip($code, $name, $why) {
    $script:Results += [pscustomobject]@{ Ma = $code; Buoc = $name; KetQua = "BO QUA"; ChiTiet = $why }
    Write-Host "  [BO QUA] $code $name  $why" -ForegroundColor DarkYellow
}
function Pause-Step { if (-not $NoPause) { Read-Host "  (Enter de sang buoc tiep)" | Out-Null } }

# ------------------------------------------------------------
# Ham goi he thong
# ------------------------------------------------------------
function NowUtc { (Get-Date).ToUniversalTime() }
function Iso($t) { $t.ToString("yyyy-MM-ddTHH:mm:ss.fffZ") }
# Do lech giua 2 moc thoi gian. $isoTo la chuoi ISO (PowerShell 5.1) hoac DateTime (PowerShell 7 tu doi)
function Seconds($fromUtc, $isoTo) {
    if ($isoTo -is [DateTime]) { $to = $isoTo.ToUniversalTime() }
    else { $to = [DateTimeOffset]::Parse([string]$isoTo).UtcDateTime }
    [math]::Round(($to - $fromUtc).TotalSeconds, 1)
}

# GET qua LB, tra ve status / body / header X-LB-Attempts (khong nem loi khi 5xx)
function Lb-Get([string]$path, [string]$ip = $TagIp) {
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, "$LbUrl$path")
    if ($ip) { [void]$req.Headers.TryAddWithoutValidation("X-Forwarded-For", $ip) }
    try {
        $res  = $script:Http.SendAsync($req).GetAwaiter().GetResult()
        $body = $res.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $vals = $null; $attempts = $null
        if ($res.Headers.TryGetValues("X-LB-Attempts", [ref]$vals)) { $attempts = [int](@($vals)[0]) }
        return [pscustomobject]@{ Status = [int]$res.StatusCode; Body = $body; Attempts = $attempts }
    } catch {
        return [pscustomobject]@{ Status = 0; Body = $_.Exception.Message; Attempts = $null }
    }
}

function Health($port) {
    try { return (Invoke-RestMethod "http://localhost:$port/actuator/health" -TimeoutSec 3).status }
    catch { return "DOWN" }
}
function Wait-Up([string[]]$names, [int]$timeout = 180) {
    $deadline = (Get-Date).AddSeconds($timeout)
    while ((Get-Date) -lt $deadline) {
        $down = @($names | Where-Object { (Health $Ports[$_]) -ne "UP" })
        if ($down.Count -eq 0) { return $true }
        Write-Host "    ... cho $($down -join ', ') khoi dong" -ForegroundColor DarkGray
        Start-Sleep 5
    }
    return $false
}

# Doc log JSON cua container tu thoi diem $sinceUtc
function Container-Json([string]$container, $sinceUtc) {
    # Lui 3 giay: dong ho Windows va may ao Docker co the lech nhau mot chut
    $lines = docker logs $container --since (Iso $sinceUtc.AddSeconds(-3)) 2>&1 | ForEach-Object { "$_" } | Where-Object { $_.StartsWith("{") }
    foreach ($l in $lines) { try { $l | ConvertFrom-Json } catch { } }
}
function Lb-Requests($sinceUtc) {
    @(Container-Json $Lb $sinceUtc | Where-Object { $_.event -eq "request" -and $_.clientIp -eq $TagIp })
}
function Wait-LbEvent([string]$event, [string]$backend, $sinceUtc, [int]$timeout) {
    $deadline = (Get-Date).AddSeconds($timeout)
    while ((Get-Date) -lt $deadline) {
        $e = @(Container-Json $Lb $sinceUtc | Where-Object { $_.event -eq $event -and $_.backend -eq $backend }) | Select-Object -First 1
        if ($e) { return $e }
        Start-Sleep 2
    }
    return $null
}
function Last-Snapshot {
    $s = @(Container-Json $Detector (NowUtc).AddSeconds(-25) | Where-Object { $_.event -eq "snapshot" }) | Select-Object -Last 1
    if ($s) { return $s.message } else { return "(chua co snapshot)" }
}

# Tim canh bao trong alerts-* tu thoi diem $sinceUtc (target = "" nghia la khong loc)
function Find-Alert([string]$rule, [string]$target, $sinceUtc) {
    $filter = @(
        @{ term  = @{ rule = $rule } },
        @{ range = @{ "@timestamp" = @{ gte = (Iso $sinceUtc.AddSeconds(-5)) } } }
    )
    if ($target) { $filter += @{ term = @{ target = $target } } }
    $body = @{ size = 1; sort = @(@{ "@timestamp" = @{ order = "asc" } }); query = @{ bool = @{ filter = $filter } } } |
            ConvertTo-Json -Depth 10
    try {
        $r = Invoke-RestMethod "$EsUrl/alerts-*/_search" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 5
        $hit = @($r.hits.hits) | Select-Object -First 1
        if ($hit) { return $hit._source }
    } catch { }
    return $null
}
# Cho canh bao, moi 10 giay in dong snapshot de thay so request tang dan
function Wait-Alert([string]$rule, [string]$target, $sinceUtc, [int]$timeout, [switch]$ShowSnapshot) {
    $deadline = (Get-Date).AddSeconds($timeout)
    $nextShow = Get-Date
    while ((Get-Date) -lt $deadline) {
        $a = Find-Alert $rule $target $sinceUtc
        if ($a) { return $a }
        if ($ShowSnapshot -and (Get-Date) -ge $nextShow) {
            Write-Host "    ... detector: $(Last-Snapshot)" -ForegroundColor DarkGray
            $nextShow = (Get-Date).AddSeconds(10)
        }
        Start-Sleep 3
    }
    return $null
}
function Show-Alert($a, $fromUtc) {
    Write-Host ("    CANH BAO [{0}] {1}: {2}" -f $a.severity, $a.rule, $a.message) -ForegroundColor Yellow
    if ($fromUtc) { Write-Host "    Do tre tu luc gay su co: $(Seconds $fromUtc $a.'@timestamp') s" -ForegroundColor Yellow }
}

# ============================================================
# BAT DAU
# ============================================================
Title "0. Kiem tra truoc khi demo"
$es = $false; try { Invoke-RestMethod "$EsUrl/_cluster/health" -TimeoutSec 5 | Out-Null; $es = $true } catch { }
Check "P0" "Elasticsearch san sang" $es
$allUp = Wait-Up @("web-1", "web-2", "web-3") 60
Check "P0" "3 backend UP" $allUp
$lbOk = (Lb-Get "/api/movies").Status -eq 200
Check "P0" "LB tra loi qua cong 8080" $lbOk
if (-not ($es -and $allUp -and $lbOk)) {
    Write-Host "  He thong chua san sang. Chay: docker compose -f elk-stack/docker-compose.elk.yml up -d ; docker compose up -d" -ForegroundColor Red
    exit 1
}
Info "Khoi dong lai bo phat hien de xoa cooldown cu (canh bao nao cung bao duoc ngay)..."
docker compose restart anomaly-detector *> $null
Start-Sleep 12
Info "Detector: $(Last-Snapshot)"
Todo "DONG tab web CineHub (de request cua giao dien khong lan vao buoc 1)."
Explain "He thong co 1 cong vao duy nhat la LB :8080; LB chia tai cho 3 backend va ghi log JSON; Filebeat dua log vao Elasticsearch; bo phat hien doc log moi 10s va ghi canh bao vao alerts-*."
Pause-Step

# ------------------------------------------------------------
Title "1. Load Balancer khi moi thu khoe (L1, L5, L6)"
$t = NowUtc
1..6 | ForEach-Object { [void](Lb-Get "/api/movies") }
Start-Sleep 1
$seq = @(Lb-Requests $t | Select-Object -Last 6 | ForEach-Object { $_.backend -replace "^http://", "" -replace ":8080$", "" })
Info "Thu tu backend phuc vu 6 request: $($seq -join ' -> ')"
$noRepeat = $true; for ($i = 1; $i -lt $seq.Count; $i++) { if ($seq[$i] -eq $seq[$i - 1]) { $noRepeat = $false } }
Check "L1" "Round-robin" ($seq.Count -eq 6 -and @($seq | Sort-Object -Unique).Count -eq 3 -and $noRepeat) "($($seq -join ','))"
Explain "Round-robin: lan luot web-1, web-2, web-3, chi chon trong cac backend dang khoe."

$t = NowUtc
[void](Lb-Get "/api/movies" "203.0.113.5")
[void](Lb-Get "/api/movies" "")
Start-Sleep 1
$ips = @(Container-Json $Lb $t | Where-Object { $_.event -eq "request" } | Select-Object -Last 2 | ForEach-Object { $_.clientIp })
Info "clientIp trong log LB: $($ips -join ' , ')"
Check "L5" "X-Forwarded-For" ($ips.Count -eq 2 -and $ips[0] -eq "203.0.113.5" -and $ips[1] -ne "203.0.113.5") "($($ips -join ', '))"
Explain "Do NAT cua Docker Desktop, moi request tu may that co IP 172.18.0.1. LB doc IP goc tu X-Forwarded-For (LB_TRUST_XFF=true) de kiem thu cac luat theo IP; khi trien khai that se tat."

$movies = Lb-Get "/api/movies"
$urls = @([regex]::Matches($movies.Body, '"(https?://[^"]*/uploads/[^"]*)"') | ForEach-Object { $_.Groups[1].Value })
if ($urls.Count -gt 0) {
    Info "Vi du URL anh: $($urls[0])"
    $good = @($urls | Where-Object { $_ -like "http://localhost:8080/uploads/*" }).Count
    Check "L6" "URL media theo dia chi LB" ($good -eq $urls.Count) "($good/$($urls.Count) URL dung)"
} else {
    Skip "L6" "URL media theo dia chi LB" "khong co phim nao co anh /uploads/ trong /api/movies"
}
Explain "Backend luu duong dan tuong doi, dung URL day du tu X-Forwarded-Host/Port ma LB gui, nen anh hien dung du backend nao phuc vu."
Pause-Step

# ------------------------------------------------------------
Title "2. Luu luong tang dot bien - R7 TRAFFIC_SPIKE (z-score) (D7, D8)"
if ($SkipManual) { Skip "D7" "R7 TRAFFIC_SPIKE" "-SkipManual" } else {
    Info "Nen hien tai: $(Last-Snapshot)"
    Explain "R7 so voi chinh he thong 30 phut truoc: z = (hien tai - trung binh) / max(do lech, 5); bao khi z > 3 va hien tai >= 30 request/phut."
    Todo "MO WEB va dung lien tuc ~1 phut (mo nhieu phim, quay lai, tim kiem, F5). Script dang cho canh bao (toi da $ManualTimeout s)."
    $t = NowUtc
    $a = Wait-Alert "TRAFFIC_SPIKE" "" $t $ManualTimeout -ShowSnapshot
    if ($a) { Show-Alert $a $null }
    Check "D7" "R7 TRAFFIC_SPIKE" ($null -ne $a) $(if ($a) { "z = $([math]::Round([double]$a.value, 2))" } else { "khong co canh bao" })
    Explain "Day la nguoi dung that nen la duong tinh gia: z-score nhay khi he thong vang. Sau 1-2 phut phut dong vua roi vao nen, z giam: tu thich nghi, nhung cung co the bi 'dau doc nen'."
    Info "Dung web them 20 giay nua de thay cooldown (D8)..."
    Start-Sleep 20
    $sup = @(Container-Json $Detector $t | Where-Object { $_.event -eq "alert_suppressed" }).Count
    if ($a) { Check "D8" "Cooldown chong bao trung" ($sup -gt 0) "($sup lan 'Bo qua canh bao trung')" }
    else    { Skip "D8" "Cooldown chong bao trung" "khong co canh bao R7 de kiem tra" }
}
Pause-Step

# ------------------------------------------------------------
Title "3. Do mat khau - R3 BRUTE_FORCE_LOGIN (D3)"
if ($SkipManual) { Skip "D3" "R3 BRUTE_FORCE_LOGIN" "-SkipManual" } else {
    Todo "Tren web, DANG NHAP SAI 11 lan trong 1 phut (nguong 10). Script dang cho canh bao (toi da $ManualTimeout s)."
    $t = NowUtc
    $a = Wait-Alert "BRUTE_FORCE_LOGIN" "" $t $ManualTimeout
    if ($a) { Show-Alert $a $null }
    Check "D3" "R3 BRUTE_FORCE_LOGIN" ($null -ne $a) $(if ($a) { "IP $($a.target), $($a.value) lan sai" } else { "khong co canh bao" })
    Explain "R3 dem so phan hoi 401 tren /api/auth/login theo tung IP trong 60s."
}
Pause-Step

# ------------------------------------------------------------
Title "4. Su co mot backend: tat web-2 (L3, L2, D6)"
$t0 = NowUtc
Info "$((Get-Date).ToString('HH:mm:ss.fff'))  docker compose stop web-2"
docker compose stop web-2 *> $null
$retry = $null
for ($i = 0; $i -lt 6 -and -not $retry; $i++) {
    $r = Lb-Get "/api/movies"
    Info "  request $($i + 1): HTTP $($r.Status), X-LB-Attempts = $($r.Attempts)"
    if ($r.Attempts -ge 2) { $retry = $r }
}
Check "L3" "Retry khi backend vua chet" ($null -ne $retry -and $retry.Status -eq 200) "(HTTP $($retry.Status), $($retry.Attempts) lan thu)"
Explain "Health check chua kip phat hien; request roi vao web-2 duoc gui lai sang backend khac, nguoi dung khong thay loi."

Info "Cho health check danh dau web-2 DOWN..."
$down = Wait-LbEvent "backend_down" "http://web-2:8080" $t0 45
Check "L2" "Health check loai web-2" ($null -ne $down) $(if ($down) { "sau $(Seconds $t0 $down.'@timestamp') s" } else { "" })
Start-Sleep 2   # LB cap nhat danh sach backend khoe ngay sau vong health check
$t = NowUtc
1..6 | ForEach-Object { [void](Lb-Get "/api/movies") }
Start-Sleep 1
$served = @(Lb-Requests $t | ForEach-Object { $_.backend -replace "^http://", "" -replace ":8080$", "" })
Check "L2" "Khong con chia tai cho web-2" ($served.Count -gt 0 -and -not ($served -contains "web-2")) "($($served -join ','))"

$a = Wait-Alert "BACKEND_DOWN" "http://web-2:8080" $t0 60
if ($a) { Show-Alert $a $t0 }
Check "D6" "Canh bao R6 BACKEND_DOWN" ($null -ne $a) $(if ($a) { "do tre $(Seconds $t0 $a.'@timestamp') s" } else { "" })
Explain "Health check moi 5s: 3 lan loi -> DOWN, 2 lan thanh cong -> UP. Su kien DOWN duoc ghi log va bo phat hien bien thanh canh bao."
Pause-Step

Info "docker compose start web-2 (Spring can 30-60s)..."
$t1 = NowUtc
docker compose start web-2 *> $null
$ok = Wait-Up @("web-2") 180
$up = if ($ok) { Wait-LbEvent "backend_up" "http://web-2:8080" $t1 30 } else { $null }
Check "L2" "web-2 duoc nhan lai" ($null -ne $up) $(if ($up) { "LB ghi UP sau $(Seconds $t1 $up.'@timestamp') s" } else { "" })
Pause-Step

# ------------------------------------------------------------
Title "5. Su co toan bo backend (L4, D4)"
$t0 = NowUtc
Info "docker compose stop web-1 web-2 web-3"
docker compose stop web-1 web-2 web-3 *> $null
$r = $null
for ($i = 0; $i -lt 20; $i++) {
    $r = Lb-Get "/api/movies"
    if ($r.Status -eq 503) { break }
    Start-Sleep 3
}
Info "LB tra ve: HTTP $($r.Status) - $($r.Body)"
Check "L4" "Tat ca backend chet -> 503" ($r.Status -eq 503) ""
Explain "Khong con backend khoe thi LB tra 503 ngay thay vi de nguoi dung cho."

if ($SkipManual) { Skip "D4" "R4 ERROR_RATE_5XX" "-SkipManual" } else {
    Todo "MO WEB va nhan F5 khoang 8-10 lan trong 1 phut (can >= 20 request trong 60s). Script dang cho canh bao (toi da $ManualTimeout s)."
    $a = Wait-Alert "ERROR_RATE_5XX" "" $t0 $ManualTimeout -ShowSnapshot
    if ($a) { Show-Alert $a $null }
    Check "D4" "R4 ERROR_RATE_5XX" ($null -ne $a) $(if ($a) { "ti le 5xx $([math]::Round([double]$a.value, 1))%" } else { "khong co canh bao" })
    Explain "Moi request 503 van duoc ghi log nen R4 thay ti le loi vuot 5%. R4 chi xet khi co it nhat 20 request."
}
Info "docker compose start web-1 web-2 web-3 ..."
docker compose start web-1 web-2 web-3 *> $null
$ok = Wait-Up @("web-1", "web-2", "web-3") 240
Check "P5" "3 backend UP tro lai" $ok
Start-Sleep 12   # cho LB health check nhan lai (2 lan x 5s)
Pause-Step

# ------------------------------------------------------------
Title "6. Tong ket"
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}"
$t = (NowUtc).AddHours(-1)
try {
    $body = @{ size = 0; query = @{ range = @{ "@timestamp" = @{ gte = (Iso $t) } } }; aggs = @{ r = @{ terms = @{ field = "rule"; size = 10 } } } } | ConvertTo-Json -Depth 10
    $agg = Invoke-RestMethod "$EsUrl/alerts-*/_search" -Method Post -ContentType "application/json" -Body $body
    Info "Canh bao trong 1 gio qua:"
    foreach ($b in @($agg.aggregations.r.buckets)) { Info ("  {0,-20} {1}" -f $b.key, $b.doc_count) }
} catch { }

Write-Host ""
$script:Results | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
$fail = @($script:Results | Where-Object KetQua -eq "LOI").Count
if ($fail -eq 0) { Write-Host "  TAT CA DAT" -ForegroundColor Green } else { Write-Host "  Co $fail buoc LOI" -ForegroundColor Red }
Write-Host "  Kibana canh bao: http://localhost:5601/app/discover  (data view CineHub alerts)"
Write-Host "  Dashboard:       http://localhost:5601/app/dashboards#/view/cinehub-traffic"