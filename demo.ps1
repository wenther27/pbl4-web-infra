# ============================================================
# CineHub - Demo he thong nen cho de tai Load Balancing + ELK
# Chay:  powershell -ExecutionPolicy Bypass -File .\demo.ps1
# Tu dong (khong dung giua cac buoc):  ... -File .\demo.ps1 -NoPause
# ============================================================
param([switch]$NoPause, [switch]$NoBrowser)

$ErrorActionPreference = "Continue"   # PS 5.1: stderr cua docker khong phai loi
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Set-Location $PSScriptRoot

$Compose  = @("-f", "docker-compose.yml")
$Ports    = 8081, 8082, 8083
$DbPass   = "change-me-for-local-development"
# Neu da tao .env (xem .env.example) thi dung dung mat khau MySQL trong do
if (Test-Path ".env") {
    $envPass = Select-String -Path ".env" -Pattern '^\s*MYSQL_ROOT_PASSWORD\s*=\s*(.+)$' | Select-Object -First 1
    if ($envPass) { $DbPass = $envPass.Matches[0].Groups[1].Value.Trim().Trim('"') }
}
$Primary  = "cinehub-infrastructure-mysql-primary-1"
$Replica  = "cinehub-infrastructure-mysql-replica-1"
$script:Results = @()

function Title($text) {
    Write-Host ""
    Write-Host ("=" * 64) -ForegroundColor DarkGray
    Write-Host " $text" -ForegroundColor Cyan
    Write-Host ("=" * 64) -ForegroundColor DarkGray
}
function Check($name, [bool]$ok, $detail = "") {
    $script:Results += [pscustomobject]@{ Buoc = $name; KetQua = $(if ($ok) { "DAT" } else { "LOI" }) }
    if ($ok) { Write-Host "  [DAT] $name $detail" -ForegroundColor Green }
    else     { Write-Host "  [LOI] $name $detail" -ForegroundColor Red }
}
function Pause-Step {
    if (-not $NoPause) { Read-Host "  (Enter de sang buoc tiep)" | Out-Null }
}
function Health($port) {
    try { return (Invoke-RestMethod "http://localhost:$port/actuator/health" -TimeoutSec 3).status }
    catch { return "DOWN" }
}
function StatusCode([scriptblock]$call) {
    try { & $call | Out-Null; return 200 }
    catch { return [int]$_.Exception.Response.StatusCode }
}
function Sql($container, $query) {
    docker exec -e MYSQL_PWD=$DbPass $container mysql -uroot --default-character-set=utf8mb4 -N -e $query
}

function Fe($port) { "http://localhost:5173/?api=http://localhost:$port/api" }
function Open-Fe($url) { if (-not $NoBrowser) { Start-Process $url } }
function FeHint($text) { Write-Host "  [FE] $text" -ForegroundColor Magenta }
function FeUp { try { Invoke-WebRequest "http://127.0.0.1:5173" -UseBasicParsing -TimeoutSec 5 | Out-Null; $true } catch { $false } }

# ------------------------------------------------------------
Title "0. Khoi dong: 2 MySQL (primary/replica) + 3 backend (web-1/2/3) + giao dien web"
# Lan dau se build image backend (vai phut), cac lan sau dung cache
docker compose @Compose up -d --build *> $null
Write-Host "  Doi 3 backend san sang..."
for ($i = 0; $i -lt 40; $i++) {
    $up = @($Ports | Where-Object { (Health $_) -eq "UP" }).Count
    if ($up -eq 3) { break }
    Start-Sleep 3
}
docker compose @Compose ps --format "table {{.Service}}\t{{.Status}}\t{{.Ports}}"
Check "3 backend dang chay" ($up -eq 3) "($up/3 UP)"

Write-Host "  Bat ELK (Elasticsearch + Kibana + Filebeat)..."
docker compose -f elk-stack/docker-compose.elk.yml up -d *> $null

$fe = Join-Path $PSScriptRoot "web-server\frontend"
if (-not (FeUp)) {
    Write-Host "  Bat FE (Vite) trong cua so rieng..."
    $install = if (Test-Path (Join-Path $fe "node_modules")) { "" } else { "npm install; " }
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$fe'; ${install}npm run dev"
    for ($i = 0; $i -lt 60 -and -not (FeUp); $i++) { Start-Sleep 2 }
}
Check "FE chay tai http://localhost:5173" (FeUp)
Open-Fe (Fe 8081)
FeHint "Trang chu da mo, dang goi web-1. Goc phai duoi: 'Phuc vu boi: web-1' + nut doi server."
Pause-Step

# ------------------------------------------------------------
Title "1. Ba server chay song song, moi server tu bao ten (X-Instance-Id)"
$seen = @()
foreach ($p in $Ports) {
    $r  = Invoke-WebRequest "http://localhost:$p/api/movies?size=1" -UseBasicParsing
    $id = $r.Headers["X-Instance-Id"]
    $seen += $id
    Write-Host "  localhost:$p  ->  X-Instance-Id = $id"
}
Check "Moi cong tra ve dung instance" (($seen -join ",") -eq "web-1,web-2,web-3")
FeHint "Bam nut web-2, web-3 o goc phai duoi: cung giao dien, cung du lieu, khung doi thanh web-2/web-3."
Pause-Step

# ------------------------------------------------------------
Title "2. Stateless: dang nhap o web-1, dung token goi web-3"
$login = Invoke-RestMethod "http://localhost:8081/api/auth/login" -Method Post `
    -ContentType "application/json" -Body '{"username":"admin","password":"123456"}'
Write-Host "  Dang nhap tai web-1: $($login.username) / $($login.role)"
$fav = StatusCode { Invoke-RestMethod "http://localhost:8083/api/favorites" -Headers @{ Authorization = "Bearer $($login.token)" } }
Write-Host "  Goi /api/favorites (can dang nhap) tai web-3 bang token do -> $fav"
Check "Token dung duoc tren server khac" ($fav -eq 200)
FeHint "Bam web-1, dang nhap admin / 123456. Bam sang web-3: VAN dang nhap (JWT, khong can session tren server)."
Pause-Step

# ------------------------------------------------------------
Title "3. Du lieu dung chung + replication: dang ky o web-2 -> co tren replica"
$user = "demo" + (Get-Date -Format "HHmmss")
$body = @{ username = $user; email = "$user@cinehub.com"; password = "12345678" } | ConvertTo-Json
$reg  = Invoke-RestMethod "http://localhost:8082/api/auth/register" -Method Post -ContentType "application/json" -Body $body
Write-Host "  Dang ky tai web-2: $($reg.username) / $($reg.role)  (ghi vao PRIMARY)"
Start-Sleep 1
$onReplica = Sql $Replica "SELECT username FROM cinehub_db.users WHERE username='$user'"
Write-Host "  Doc tren REPLICA: $onReplica"
Check "Replica tu co user moi" ($onReplica -eq $user)
FeHint "Dang xuat, dang nhap '$user' / 12345678 tren web-1 hoac web-3: tai khoan tao o web-2 van dung duoc."
Pause-Step

# ------------------------------------------------------------
Title "4. Replica chi doc: ghi truc tiep vao replica phai bi tu choi"
# Xoa 1 dong KHONG ton tai: neu replica lo chua chan ghi thi cung khong lam lech du lieu
$out = docker exec -e MYSQL_PWD=$DbPass $Replica mysql -uroot -e "DELETE FROM cinehub_db.users WHERE username='__khong_ton_tai__'" 2>&1 | ForEach-Object { "$_" } | Out-String
Write-Host ("  " + ($out.Trim() -split "`n" | Select-Object -First 1))
Check "Replica chan ghi (super_read_only)" ($out -match "super-read-only")
Pause-Step

# ------------------------------------------------------------
Title "5. Phan quyen: khong token bi chan, user thuong goi API admin -> 403"
$u = Invoke-RestMethod "http://localhost:8081/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ username = $user; password = "12345678" } | ConvertTo-Json)
$cNo  = StatusCode { Invoke-RestMethod "http://localhost:8081/api/users" }
$c403 = StatusCode { Invoke-RestMethod "http://localhost:8081/api/users" -Headers @{ Authorization = "Bearer $($u.token)" } }
$c200 = StatusCode { Invoke-RestMethod "http://localhost:8081/api/users" -Headers @{ Authorization = "Bearer $($login.token)" } }
Write-Host "  Khong token          -> $cNo"
Write-Host "  USER goi /api/users  -> $c403"
Write-Host "  ADMIN goi /api/users -> $c200"
Check "Phan quyen dung" ($cNo -in 401, 403 -and $c403 -eq 403 -and $c200 -eq 200)
FeHint "Dang nhap admin -> vao duoc trang quan tri: http://localhost:5173/pages/admin/  ; tai khoan USER thi khong."
Pause-Step

# ------------------------------------------------------------
Title "6. Log JSON co cau truc -> Filebeat -> Elasticsearch -> Kibana"
FeHint "Moi thao tac tren web deu sinh 1 dong log JSON o server dang phuc vu."
$line = docker logs cinehub-infrastructure-web-2-1 --tail 50 2>&1 | Where-Object { "$_" -match '"path"' } | Select-Object -Last 1
Write-Host "  $line"
$json = "$line" | ConvertFrom-Json -ErrorAction SilentlyContinue
if ($json) {
    Write-Host ("  -> instance={0}  {1} {2}  status={3}  {4}ms  ip={5}" -f `
        $json.instance, $json.httpMethod, $json.path, $json.status, $json.durationMs, $json.clientIp) -ForegroundColor Yellow
}
Check "Log la JSON, co instance/path/status/durationMs" ($null -ne $json -and $json.path)

Write-Host "  Filebeat doc log do -> Elasticsearch. Dem request 15 phut gan nhat theo instance:"
$es = "http://localhost:9200"
for ($i = 0; $i -lt 40; $i++) {
    try { Invoke-RestMethod "$es/_cluster/health" -TimeoutSec 3 | Out-Null; break } catch { Start-Sleep 3 }
}
Start-Sleep 5   # Filebeat gui theo lo, tre vai giay
$query = '{"size":0,"query":{"range":{"@timestamp":{"gte":"now-15m"}}},"aggs":{"theo_instance":{"terms":{"field":"instance"}}}}'
$buckets = @()
try { $buckets = @((Invoke-RestMethod "$es/weblogs-*/_search" -Method Post -ContentType "application/json" -Body $query).aggregations.theo_instance.buckets) } catch { }
foreach ($b in $buckets) { Write-Host ("    {0}: {1} request" -f $b.key, $b.doc_count) -ForegroundColor Yellow }
Check "Log da vao Elasticsearch (ELK)" ($buckets.Count -ge 3) "($($buckets.Count) instance)"
powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "elk-stack\setup-kibana.ps1") *> $null
Open-Fe "http://localhost:5601/app/dashboards#/view/cinehub-traffic"
FeHint "Kibana: dashboard 'CineHub - Giam sat traffic' tu refresh 10s. Bam web-1/2/3 tren web -> bieu do nhay theo."
FeHint "Kibana > Discover: go  instance : web-2 and status >= 400  de loc log."
Pause-Step

# ------------------------------------------------------------
Title "7. Mot server sap -> health check phat hien (ly do can Load Balancer)"
docker stop cinehub-infrastructure-web-2-1 | Out-Null
Write-Host "  Da tat web-2."
foreach ($p in $Ports) { Write-Host "  localhost:$p  ->  $(Health $p)" }
$down = (Health 8082) -eq "DOWN"
FeHint "Bam nut web-2: khung chuyen DO 'Mat ket noi backend'. Bam web-1/web-3 van chay binh thuong."
FeHint "-> Chua co Load Balancer thi nguoi dung phai tu doi server. Tuan sau LB se tu lam viec nay."
Pause-Step
Write-Host "  Bat lai web-2..."
docker start cinehub-infrastructure-web-2-1 | Out-Null
for ($i = 0; $i -lt 30 -and (Health 8082) -ne "UP"; $i++) { Start-Sleep 2 }
Write-Host "  localhost:8082  ->  $(Health 8082)"
Check "Phat hien web-2 DOWN va khoi phuc" ($down -and (Health 8082) -eq "UP")
FeHint "Bam web-2 lan nua: hoat dong lai."

# ------------------------------------------------------------
Sql $Primary "DELETE FROM cinehub_db.users WHERE username='$user'" | Out-Null

Title "TONG KET"
$script:Results | Format-Table -AutoSize
$fail = @($script:Results | Where-Object KetQua -eq "LOI").Count
if ($fail -eq 0) { Write-Host "  TAT CA DAT" -ForegroundColor Green }
else { Write-Host "  Co $fail buoc LOI" -ForegroundColor Red }
Write-Host "  Giao dien: $(Fe 8081)"
Write-Host "  Kibana:    http://localhost:5601/app/dashboards#/view/cinehub-traffic"
