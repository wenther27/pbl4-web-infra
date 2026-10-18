# ============================================================
# Nap Data View "weblogs-*" + dashboard "CineHub - Giam sat traffic" vao Kibana.
# Chay 1 lan sau khi ELK len (chay lai cung khong sao, se ghi de):
#   powershell -ExecutionPolicy Bypass -File .\elk-stack\setup-kibana.ps1
# ============================================================
$ErrorActionPreference = "Stop"
$Kibana    = "http://localhost:5601"
$Dashboard = Join-Path $PSScriptRoot "kibana\dashboards\traffic-dashboard.ndjson"

Write-Host "Doi Kibana san sang..."
for ($i = 0; $i -lt 60; $i++) {
    try { Invoke-RestMethod "$Kibana/api/status" -TimeoutSec 3 | Out-Null; break }
    catch { Start-Sleep 3 }
}

# PowerShell 5.1 khong co -Form cho multipart -> dung curl.exe (co san tren Windows 10+)
$result = curl.exe -s -X POST "$Kibana/api/saved_objects/_import?overwrite=true" `
    -H "kbn-xsrf: true" --form "file=@$Dashboard" | ConvertFrom-Json

if ($result.success) {
    Write-Host "Da nap $($result.successCount) doi tuong (data view + 5 bieu do + dashboard)." -ForegroundColor Green
    Write-Host "Mo: $Kibana/app/dashboards#/view/cinehub-traffic" -ForegroundColor Yellow
} else {
    Write-Host "Nap that bai:" -ForegroundColor Red
    $result | ConvertTo-Json -Depth 6
    exit 1
}
