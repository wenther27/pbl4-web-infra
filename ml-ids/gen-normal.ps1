param(
    [int]$Minutes = 5,
    [string]$Base = "http://localhost:8080"
)

$titles = @(
    "avengers", "spider man", "doraemon", "conan", "one piece", "naruto", "batman", "frozen",
    "toy story", "harry potter", "fast and furious", "inception", "interstellar", "titanic",
    "lật mặt", "bố già", "mắt biếc", "nhà bà nữ", "em chưa 18", "hai phượng", "đất rừng phương nam",
    "the", "love", "king", "war", "star", "man", "a", "2024", "phần 2", "tập 1", "kimetsu", "attack on titan"
)
$sorts = @("id", "title", "releaseDate", "rating", "duration", "createdAt")
$types = @("movie", "series", "anime")
$statuses = @("released", "upcoming", "ongoing")

function Pick($arr) { $arr[(Get-Random -Maximum $arr.Count)] }
function Esc($s) { [uri]::EscapeDataString($s) }
function Hit($path) {
    try {
        Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 -Uri ($Base + $path) | Out-Null
        return $true
    } catch {
        return $false
    }
}

$genreSlugs = @()
try { $genreSlugs = @((Invoke-RestMethod "$Base/api/genres") | ForEach-Object { $_.slug } | Where-Object { $_ }) } catch {}
$movieIds = @()
try { $movieIds = @((Invoke-RestMethod "$Base/api/movies?page=0&size=50").content | ForEach-Object { $_.id }) } catch {}
Write-Host "Tim thay $($genreSlugs.Count) the loai, $($movieIds.Count) phim. Chay $Minutes phut..."

$end = (Get-Date).AddMinutes($Minutes)
$ok = 0; $fail = 0
while ((Get-Date) -lt $end) {
    $page = Get-Random -Maximum 5
    $size = Pick @(10, 12, 20, 24)
    $paths = @(
        "/api/movies?page=$page&size=$size",
        "/api/movies?title=$(Esc (Pick $titles))&page=0&size=$size",
        "/api/movies?title=$(Esc (Pick $titles))&movieType=$(Pick $types)&page=$page&size=$size",
        "/api/movies?movieType=$(Pick $types)&status=$(Pick $statuses)&page=$page&size=$size&sortBy=$(Pick $sorts)&direction=$(Pick @('asc','desc'))",
        "/api/movies?page=$page&size=$size&sortBy=$(Pick $sorts)&direction=$(Pick @('asc','desc'))",
        "/api/movies/trending",
        "/api/movies/type?movieType=$(Pick $types)",
        "/api/genres"
    )
    if ($genreSlugs.Count) { $paths += "/api/movies/genre/$(Pick $genreSlugs)" }
    if ($movieIds.Count) {
        $id = Pick $movieIds
        $paths += "/api/movies/$id/episodes"
        $paths += "/api/movies/$id/reviews"
    }
    if (Hit (Pick $paths)) { $ok++ } else { $fail++ }
    if ((($ok + $fail) % 50) -eq 0) { Write-Host "  da gui $($ok + $fail) request (ok=$ok, loi=$fail)" }
    Start-Sleep -Milliseconds (Get-Random -Minimum 100 -Maximum 300)
}
Write-Host "Xong: ok=$ok, loi=$fail"