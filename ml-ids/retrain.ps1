param(
    [switch]$RefreshNormal,
    [int]$Minutes = 60,
    [int]$FeedbackWeight = 10
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Invoke-MlIds([string[]]$Arguments) {
    docker run --rm -v "${root}:/repo" -w /repo/ml-ids pbl4-ml-ids:local python @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Lenh that bai: python $($Arguments -join ' ')" }
}

Write-Host "[1/4] Lay cac request admin danh dau bao nham tu Elasticsearch" -ForegroundColor Cyan
Invoke-MlIds @('export_feedback.py')

if ($RefreshNormal) {
    Write-Host "[2/4] Lay traffic binh thuong cua CineHub trong $Minutes phut qua" -ForegroundColor Cyan
    Invoke-MlIds @('export_normal.py', '--minutes', "$Minutes")
} else {
    Write-Host "[2/4] Giu nguyen data/cinehub-normal.jsonl (them -RefreshNormal de lay moi)" -ForegroundColor Cyan
}

Write-Host "[3/4] Train lai model" -ForegroundColor Cyan
Invoke-MlIds @('train.py', '--data', 'data/csic_database.csv', '--extra-normal', 'data/cinehub-normal.jsonl',
               '--feedback', 'data/feedback.jsonl', '--feedback-weight', "$FeedbackWeight")

$metrics = Get-Content (Join-Path $root 'ml-ids\model\metrics.json') -Raw | ConvertFrom-Json
$threshold = ([double]$metrics.threshold).ToString([Globalization.CultureInfo]::InvariantCulture)
$rulesFile = Join-Path $root 'anomaly-detection\src\main\resources\rules.yaml'
$text = [IO.File]::ReadAllText($rulesFile)
$updated = [regex]::Replace($text, '(?s)(ML_IDS:.*?threshold:[ \t]*)[0-9.]+', ('${1}' + $threshold))
if ($updated -eq $text -and $text -notmatch "(?s)ML_IDS:.*?threshold:[ \t]*$([regex]::Escape($threshold))") {
    throw "Khong tim thay 'threshold:' trong khoi ML_IDS cua rules.yaml"
}
[IO.File]::WriteAllText($rulesFile, $updated, (New-Object Text.UTF8Encoding $false))
Write-Host "      Nguong moi trong rules.yaml: ML_IDS.threshold = $threshold" -ForegroundColor Green

Write-Host "[4/4] Khoi dong lai ml-ids va anomaly-detector" -ForegroundColor Cyan
docker compose restart ml-ids anomaly-detector
if ($LASTEXITCODE -ne 0) { throw "docker compose restart that bai" }
Write-Host "Xong. Model moi train luc $($metrics.trainedAt) (UTC), nguong $threshold." -ForegroundColor Green
