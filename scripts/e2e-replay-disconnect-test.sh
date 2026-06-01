#!/usr/bin/env bash
# E2E: mid-game disconnect simulation, re-login, finish game, verify replay
set -euo pipefail
BASE="${COF_API_BASE:-http://127.0.0.1:9002/api/v1}"

json_get() {
  python3 -c "import json,sys; d=json.load(sys.stdin); $1" 2>/dev/null
}

api() {
  local method="$1" path="$2"
  shift 2
  local body="${1:-}"
  local args=(-s -X "$method" "${BASE}${path}" -H "Content-Type: application/json")
  if [[ -n "${TOKEN:-}" ]]; then
    args+=(-H "Authorization: Bearer $TOKEN")
  fi
  if [[ -n "$body" ]]; then
    args+=(-d "$body")
  fi
  curl "${args[@]}"
}

code_ok() {
  python3 -c "import json,sys; d=json.load(sys.stdin); sys.exit(0 if d.get('code')==0 else 1)" <<<"$1"
}

USER="replay_e2e_$(date +%s)"
PASS="testpass123"
echo "=== User: $USER ==="

REG=$(api POST /auth/register "{\"username\":\"$USER\",\"password\":\"$PASS\"}")
code_ok "$REG" || { echo "register failed: $REG"; exit 1; }
TOKEN=$(echo "$REG" | json_get "print(d['data']['token'])")
CLIENT_ID=$(echo "$REG" | json_get "print(d['data']['player']['clientId'])")
echo "clientId=$CLIENT_ID"

COMPUTERS=$(api GET /meta/computer-players)
COMP_ID=$(echo "$COMPUTERS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['data']['players'][0]['id'])")
echo "computerId=$COMP_ID"

LIBS=$(api GET /meta/card-libraries)
LIB_ID=$(echo "$LIBS" | python3 -c "
import json,sys
d=json.load(sys.stdin)
libs=d.get('data',{}).get('libraries') or d.get('data') or []
if isinstance(libs, dict): libs=libs.get('libraries',[])
print(libs[0]['id'] if libs else '1')
")

CREATE=$(api POST /rooms "{\"settings\":{\"minPlayers\":2,\"maxPlayers\":4,\"isPublic\":true,\"libraryIds\":[\"$LIB_ID\"]},\"computerIds\":[\"$COMP_ID\"]}")
code_ok "$CREATE" || { echo "create room failed: $CREATE"; exit 1; }
ROOM_ID=$(echo "$CREATE" | json_get "print(d['data']['room']['id'])")
echo "roomId=$ROOM_ID"

START=$(api POST "/rooms/$ROOM_ID/start")
code_ok "$START" || { echo "start failed: $START"; exit 1; }
GAME_ID=$(echo "$START" | json_get "print(d['data']['game']['id'])")
echo "gameId=$GAME_ID"

LOAD=$(api POST "/rooms/$ROOM_ID/loading-progress" '{"loaded":99,"total":99,"done":true}')
code_ok "$LOAD" || echo "loading-progress warn: $LOAD"

sleep 2
GAME=$(api GET "/games/$GAME_ID")
STATUS=$(echo "$GAME" | json_get "print(d['data']['game']['status'])")
echo "status after load: $STATUS"

# Play once if playing
if [[ "$STATUS" == "playing" ]]; then
  api POST "/games/$GAME_ID/play-card" >/dev/null || true
  echo "played one card before disconnect"
fi

echo "=== Simulating browser close (no API calls for 8s) ==="
sleep 8

echo "=== Re-login (new session, same account) ==="
LOGIN=$(api POST /auth/login "{\"username\":\"$USER\",\"password\":\"$PASS\"}")
code_ok "$LOGIN" || { echo "login failed: $LOGIN"; exit 1; }
TOKEN=$(echo "$LOGIN" | json_get "print(d['data']['token'])")

BOOT=$(api GET /session/bootstrap)
echo "$BOOT" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
print('bootstrap room:', (d.get('currentRoom') or {}).get('id'))
print('bootstrap game:', (d.get('currentGame') or {}).get('id'), 'status:', (d.get('currentGame') or {}).get('status'))
"

CUR_GAME=$(echo "$BOOT" | json_get "print((d.get('data') or {}).get('currentGame',{}).get('id') or '')")
[[ -n "$CUR_GAME" ]] && GAME_ID="$CUR_GAME"

finish_game() {
  local i=0
  while [[ $i -lt 400 ]]; do
  GAME=$(api GET "/games/$GAME_ID")
  STATUS=$(echo "$GAME" | json_get "print(d['data']['game']['status'])")
  if [[ "$STATUS" == "finished" ]]; then
    echo "Game finished after $i steps"
    return 0
  fi
  if [[ "$STATUS" != "playing" ]]; then
    api POST "/rooms/$ROOM_ID/loading-progress" '{"loaded":99,"total":99,"done":true}' >/dev/null 2>&1 || true
    sleep 1
    i=$((i+1))
    continue
  fi
  TURN=$(echo "$GAME" | python3 -c "
import json,sys
g=json.load(sys.stdin)['data']['game']
ti=g.get('turnIndex',0)
p=g['players'][ti]
print(p['clientId'])
")
  if [[ "$TURN" == "$CLIENT_ID" ]]; then
    api POST "/games/$GAME_ID/play-card" >/dev/null 2>&1 || true
  else
    api POST "/games/$GAME_ID/ring-bell" >/dev/null 2>&1 || true
  fi
  sleep 0.35
  i=$((i+1))
  done
  return 1
}

echo "=== Finish game after reconnect ==="
finish_game || { echo "timeout finishing game"; exit 1; }

sleep 2
PROFILE=$(api GET "/profile/$CLIENT_ID")
GAME_HIST=$(echo "$PROFILE" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']['profile']
hist=d.get('history') or []
print('history count', len(hist))
if hist: print('latest gameId', hist[0].get('gameId'))
")

REPLAY=$(api GET "/profile/$CLIENT_ID/games/$GAME_ID/replay")
echo "$REPLAY" | python3 -c "
import json,sys
d=json.load(sys.stdin)
if d.get('code')!=0:
  print('REPLAY API FAIL', d); sys.exit(1)
r=d['data']['replay']
lt=r.get('logText') or ''
rj=r.get('replayJson') or ''
print('logText bytes', len(lt))
print('replayJson bytes', len(rj))
if rj:
  t=json.loads(rj)
  print('frames', len(t.get('frames') or []))
  print('viewer', t.get('defaultViewerId'))
if not lt and not rj:
  print('NO REPLAY DATA'); sys.exit(1)
print('REPLAY OK')
"

PGPASSWORD=123123 psql -h 127.0.0.1 -U postgres -d cof_db -t -c \
  "SELECT length(log_text), length(replay_json) FROM cof_match_history WHERE game_id='$GAME_ID' LIMIT 1;"

echo "=== E2E PASSED ==="
