# API smoke test against running cof-boot (default http://127.0.0.1:9002)
$Base = "http://127.0.0.1:9002"
$Failed = 0

function Test-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [string]$Body = $null,
        [int[]]$ExpectStatus = @(200)
    )
    try {
        $params = @{
            Uri = "$Base$Path"
            Method = $Method
            Headers = $Headers
            ContentType = "application/json"
        }
        if ($Body) { $params.Body = $Body }
        $resp = Invoke-WebRequest @params -UseBasicParsing
        $status = [int]$resp.StatusCode
        if ($ExpectStatus -notcontains $status) {
            Write-Host "FAIL $Name status=$status expected=$($ExpectStatus -join ',')"
            $Failed++
            return $null
        }
        Write-Host "OK   $Name ($status)"
        if ($resp.Content) {
            return $resp.Content | ConvertFrom-Json
        }
        return $null
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        if ($ExpectStatus -contains $status) {
            Write-Host "OK   $Name ($status)"
            return $null
        }
        Write-Host "FAIL $Name status=$status msg=$($_.Exception.Message)"
        $Failed++
        return $null
    }
}

$suffix = Get-Random
$user = "smoke$suffix"
$pass = "secret123"

Test-Api "GET bootstrap" GET "/api/v1/session/bootstrap" | Out-Null
Test-Api "GET card-libraries" GET "/api/v1/meta/card-libraries" | Out-Null
Test-Api "GET computer-players" GET "/api/v1/meta/computer-players" | Out-Null
Test-Api "GET pmv-index" GET "/api/v1/meta/pmv-index" | Out-Null
Test-Api "GET leaderboard" GET "/api/v1/leaderboard" | Out-Null

$reg = Test-Api "POST register" POST "/api/v1/auth/register" -Body (@{ username = $user; password = $pass } | ConvertTo-Json)
if (-not $reg) { exit 1 }
$token = $reg.data.token
$auth = @{ Authorization = "Bearer $token" }

Test-Api "POST login" POST "/api/v1/auth/login" -Body (@{ username = $user; password = $pass } | ConvertTo-Json) | Out-Null
Test-Api "GET bootstrap authed" GET "/api/v1/session/bootstrap" -Headers $auth | Out-Null

$create = Test-Api "POST create room" POST "/api/v1/rooms" -Headers $auth -Body '{"settings":{"minPlayers":2,"maxPlayers":8,"isPublic":true}}'
$roomId = $create.data.room.id

Test-Api "GET list rooms" GET "/api/v1/rooms?all=1" -Headers $auth | Out-Null
Test-Api "PATCH room settings" PATCH "/api/v1/rooms/$roomId/settings" -Headers $auth -Body '{"settings":{"minPlayers":2,"maxPlayers":8,"isPublic":true}}' | Out-Null
Test-Api "GET room assets" GET "/api/v1/assets/rooms/$roomId" -Headers $auth | Out-Null
Test-Api "GET card-viewer" GET "/api/v1/meta/card-viewer" -Headers $auth | Out-Null
Test-Api "POST start-vote" POST "/api/v1/rooms/$roomId/start-vote" -Headers $auth | Out-Null
Test-Api "POST chat" POST "/api/v1/rooms/$roomId/chat" -Headers $auth -Body '{"message":"hi"}' | Out-Null
Test-Api "GET profile" GET "/api/v1/profile/$($reg.data.player.clientId)" -Headers $auth | Out-Null

$start = Test-Api "POST start game" POST "/api/v1/rooms/$roomId/start" -Headers $auth
if ($start -and $start.data.game.id) {
    $gid = $start.data.game.id
    Test-Api "GET game" GET "/api/v1/games/$gid" -Headers $auth | Out-Null
    Test-Api "POST loading-progress" POST "/api/v1/rooms/$roomId/loading-progress" -Headers $auth -Body '{"loaded":1,"total":1,"done":true}' | Out-Null
}

Test-Api "POST logout" POST "/api/v1/auth/logout" -Headers $auth | Out-Null

if ($Failed -gt 0) {
    Write-Host "`n$Failed test(s) failed."
    exit 1
}
Write-Host "`nAll smoke tests passed."
