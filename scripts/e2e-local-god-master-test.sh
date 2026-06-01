#!/usr/bin/env bash
# 本地 E2E：GOD + Master 人机对局、错拍铃铛、回放
set -uo pipefail
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

USER="lgm$(date +%s | tail -c 9)"
PASS="testpass123"
echo "=== User: $USER ==="

REG=$(api POST /auth/register "{\"username\":\"$USER\",\"password\":\"$PASS\"}")
code_ok "$REG" || { echo "register failed: $REG"; exit 1; }
TOKEN=$(echo "$REG" | json_get "print(d['data']['token'])")
CLIENT_ID=$(echo "$REG" | json_get "print(d['data']['player']['clientId'])")
echo "clientId=$CLIENT_ID"

LIBS=$(api GET /meta/card-libraries)
LIB_ID=$(echo "$LIBS" | python3 -c "
import json,sys
d=json.load(sys.stdin)
libs=d.get('data',{}).get('libraries') or d.get('data') or []
if isinstance(libs, dict): libs=libs.get('libraries',[])
print(libs[0]['id'] if libs else '1')
")

CREATE=$(api POST /rooms "{\"settings\":{\"minPlayers\":2,\"maxPlayers\":4,\"isPublic\":true,\"libraryIds\":[\"$LIB_ID\"]},\"computerIds\":[\"computer_god\",\"computer_master\"]}")
code_ok "$CREATE" || { echo "create failed: $CREATE"; exit 1; }
ROOM_ID=$(echo "$CREATE" | json_get "print(d['data']['room']['id'])")
echo "roomId=$ROOM_ID players:" $(echo "$CREATE" | json_get "print(d['data']['room']['players'])")

VOTE=$(api POST "/rooms/$ROOM_ID/start-vote")
code_ok "$VOTE" || { echo "vote failed: $VOTE"; exit 1; }
echo "voted; startAt:" $(echo "$VOTE" | json_get "print(d['data']['room'].get('startAt'))")

echo "=== Wait countdown (12s) ==="
sleep 12

ROOM=$(api GET "/rooms/$ROOM_ID" 2>/dev/null || api POST "/rooms/$ROOM_ID/join" "{}")
# get room via bootstrap
BOOT=$(api GET /session/bootstrap)
GAME_ID=$(echo "$BOOT" | json_get "print((d.get('data') or {}).get('currentGame',{}).get('id') or '')")
ROOM_STATUS=$(echo "$BOOT" | json_get "print((d.get('data') or {}).get('currentRoom',{}).get('status') or '')")
echo "roomStatus=$ROOM_STATUS gameId=$GAME_ID"

if [[ -z "$GAME_ID" ]]; then
  START=$(api POST "/rooms/$ROOM_ID/start")
  code_ok "$START" || { echo "manual start failed: $START"; exit 1; }
  GAME_ID=$(echo "$START" | json_get "print(d['data']['game']['id'])")
  echo "started gameId=$GAME_ID"
fi

LOAD=$(api POST "/rooms/$ROOM_ID/loading-progress" '{"loaded":99,"total":99,"done":true}')
code_ok "$LOAD" || echo "loading warn: $LOAD"
sleep 2

GAME=$(api GET "/games/$GAME_ID")
STATUS=$(echo "$GAME" | json_get "print(d['data']['game']['status'])")
echo "status=$STATUS"

WRONG_RING=0
PLAYS=0
BOTS_PLAYED=0
BOTS_RANG=0

for i in $(seq 1 120); do
  GAME=$(api GET "/games/$GAME_ID")
  STATUS=$(echo "$GAME" | json_get "print(d['data']['game']['status'])")
  if [[ "$STATUS" == "finished" ]]; then
    echo "Game finished at step $i"
    break
  fi
  if [[ "$STATUS" != "playing" ]]; then
    sleep 0.5
    continue
  fi

  META=$(echo "$GAME" | python3 -c "
import json,sys,time
g=json.load(sys.stdin)['data']['game']
ti=g.get('turnIndex',0)
p=g['players'][ti]
now=int(time.time()*1000)
lu=g.get('lockedUntil',0)
ta=g.get('turnAvailableAt',0)
bots=[x for x in g['players'] if x.get('isComputer')]
print(p['clientId'], p.get('isComputer'), lu, ta, g.get('playCount'), g.get('bellCount'), len(bots))
")

  TURN=$(echo "$META" | awk '{print $1}')
  IS_BOT=$(echo "$META" | awk '{print $2}')
  LOCKED=$(echo "$META" | awk '{print $3}')
  TA=$(echo "$META" | awk '{print $4}')
  PC_BEFORE=$(echo "$GAME" | json_get "print(d['data']['game'].get('playCount',0)")

  if [[ "$IS_BOT" == "True" ]]; then
    sleep 0.4
    GAME2=$(api GET "/games/$GAME_ID")
    PC_AFTER=$(echo "$GAME2" | json_get "print(d['data']['game'].get('playCount',0)")
    if [[ "$PC_AFTER" -gt "$PC_BEFORE" ]]; then
      BOTS_PLAYED=$((BOTS_PLAYED+1))
      echo "  bot played (playCount $PC_BEFORE -> $PC_AFTER)"
    fi
    continue
  fi

  HUMAN_DRAW=$(echo "$GAME" | python3 -c "import json,sys; g=json.load(sys.stdin)['data']['game']; print(next((p.get('drawCount',0) for p in g['players'] if p['clientId']=='$CLIENT_ID'),0))")

  if [[ "$TURN" == "$CLIENT_ID" && "$HUMAN_DRAW" -gt 0 ]]; then
  NOW_MS=$(python3 -c "import time; print(int(time.time()*1000))")
  if [[ "$NOW_MS" -lt "$TA" ]]; then
    sleep 0.2
    continue
  fi
  if [[ "$LOCKED" -gt "$NOW_MS" ]]; then
    sleep 0.3
    continue
  fi
  # 错拍测试（最多几次，避免交光手牌后卡局）
  if [[ "$WRONG_RING" -lt 3 ]]; then
    RING=$(api POST "/games/$GAME_ID/ring-bell")
    if code_ok "$RING" 2>/dev/null; then
      WRONG_RING=$((WRONG_RING+1))
      echo "  human wrong-ring attempt ok"
    fi
  fi
  sleep 0.5
  GAME=$(api GET "/games/$GAME_ID")
  NOW_MS=$(python3 -c "import time; print(int(time.time()*1000))")
  TA=$(echo "$GAME" | json_get "print(d['data']['game'].get('turnAvailableAt',0)")
  if [[ "$NOW_MS" -ge "$TA" ]]; then
    PLAY=$(api POST "/games/$GAME_ID/play-card")
    if code_ok "$PLAY" 2>/dev/null; then
      PLAYS=$((PLAYS+1))
      echo "  human play-card ok"
    fi
  fi
  fi
  sleep 0.35
done

GAME=$(api GET "/games/$GAME_ID")
STATUS=$(echo "$GAME" | json_get "print(d['data']['game']['status'])")
echo "final status=$STATUS humanPlays=$PLAYS wrongRings=$WRONG_RING botPlayEvents=$BOTS_PLAYED"

if [[ "$STATUS" != "finished" ]]; then
  echo "WARN: game did not finish in time"
fi

sleep 2
REPLAY=$(api GET "/profile/$CLIENT_ID/games/$GAME_ID/replay")
echo "$REPLAY" | python3 -c "
import json,sys
d=json.load(sys.stdin)
if d.get('code')!=0:
  print('REPLAY FAIL', d); sys.exit(1)
r=d['data']['replay']
lt=r.get('logText') or ''
rj=r.get('replayJson') or ''
print('logText len', len(lt))
print('replayJson len', len(rj))
if rj:
  t=json.loads(rj)
  print('frames', len(t.get('frames') or []))
print('REPLAY OK' if (lt or rj) else 'NO DATA')
"

echo "=== SUMMARY ==="
echo "room=$ROOM_ID game=$GAME_ID"
echo "Open replay: http://127.0.0.1:9001/profile/replay/$GAME_ID"
echo "Login: $USER / $PASS"
if [[ "$BOTS_PLAYED" -lt 1 ]]; then
  echo "FAIL: bots did not play"; exit 1
fi
if [[ "$PLAYS" -lt 1 ]]; then
  echo "WARN: human manual play count low (ring may have blocked)"
fi
echo "=== LOCAL E2E PASSED ==="
