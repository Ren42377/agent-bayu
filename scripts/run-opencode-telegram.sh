#!/bin/bash
set -e
OPENV="/home/codespace/.config/opencode-telegram-bot/.env"
SERVE_LOG="/tmp/opencode_serve.log"
BOT_LOG="/tmp/telegram_autostart.log"
PORT=4096
API_URL="http://127.0.0.1:${PORT}"
MAX_WAIT=15
LOCK="/tmp/run-opencode-telegram.lock"
exec 9>"$LOCK"
if ! flock -n 9; then echo "another autostart is running"; exit 1; fi
if [ ! -f "$OPENV" ]; then echo "missing $OPENV"; exit 1; fi
if pgrep -f "opencode --auto" >/dev/null 2>&1; then pkill -f "opencode --auto" || true; sleep 2; fi
if ! ss -tlnp 2>/dev/null | grep -q "127.0.0.1:${PORT}"; then
  nohup opencode serve --port ${PORT} > "$SERVE_LOG" 2>&1 &
  echo "started opencode serve pid $!"
else echo "opencode serve already listening"; fi
for i in $(seq 1 $MAX_WAIT); do if curl -s "$API_URL" >/dev/null 2>&1; then echo "opencode ready after ${i}s"; break; fi; sleep 1; done
if opencode-telegram status 2>&1 | grep -q "running"; then echo "telegram already running"; else opencode-telegram start --daemon 2>&1 | tee -a "$BOT_LOG"; sleep 3; fi
BOT_TOKEN=$(grep TELEGRAM_BOT_TOKEN "$OPENV" | cut -d= -f2- | tr -d '\r\n' | xargs)
PENDING=$(curl -s "https://api.telegram.org/bot${BOT_TOKEN}/getUpdates" 2>&1 | python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d.get('result',[])))" 2>/dev/null || echo "0")
echo "pending $PENDING"
if [ "$PENDING" != "0" ]; then LAST_ID=$(curl -s "https://api.telegram.org/bot${BOT_TOKEN}/getUpdates" 2>&1 | python3 -c "import json,sys; d=json.load(sys.stdin); r=d.get('result',[]); print(max([x['update_id'] for x in r])+1 if r else 0)" 2>/dev/null || echo "0"); curl -s "https://api.telegram.org/bot${BOT_TOKEN}/getUpdates?offset=${LAST_ID}" >/dev/null 2>&1 || true; echo "cleared $LAST_ID"; fi
echo "done auto permission * allow"
