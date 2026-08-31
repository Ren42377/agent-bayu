#!/bin/bash
set -e
export PATH="$HOME/.bun/bin:$HOME/.local/bin:$HOME/.opencode/bin:/usr/local/py-utils/bin:$PATH"
LOG="/tmp/codespace-autostart.log"
exec >>"$LOG" 2>&1
echo "[$(date -Iseconds)] codespace autostart begin"
if [ -f /workspaces/agent-bayu/.env/ccbot.env ]; then
  mkdir -p "$HOME/.ccbot"
  cp /workspaces/agent-bayu/.env/ccbot.env "$HOME/.ccbot/.env"
  echo "synced ~/.ccbot/.env from .env/ccbot.env"
elif [ -f "$HOME/.ccbot/.env" ]; then
  echo "found ~/.ccbot/.env"
else
  mkdir -p "$HOME/.ccbot"
  cat > "$HOME/.ccbot/.env" <<'EEOF'
TELEGRAM_BOT_TOKEN=8370802293:AAEAZ5ZP-uMAOBIuMVUB6c1GaMp_Z65Z77s
ALLOWED_USERS=7862672686
TMUX_SESSION_NAME=ccbot
CLAUDE_COMMAND=claude --dangerously-skip-permissions
EEOF
fi
if [ -f /workspaces/agent-bayu/.env/opencode-telegram.env ]; then
  mkdir -p "$HOME/.config/opencode-telegram-bot"
  cp /workspaces/agent-bayu/.env/opencode-telegram.env "$HOME/.config/opencode-telegram-bot/.env"
  echo "synced opencode-telegram .env from .env/opencode-telegram.env"
elif [ ! -f "$HOME/.config/opencode-telegram-bot/.env" ]; then
  mkdir -p "$HOME/.config/opencode-telegram-bot"
  cat > "$HOME/.config/opencode-telegram-bot/.env" <<'EEOF'
TELEGRAM_BOT_TOKEN=8980825056:AAHfxMTFvSx9YIOUIpb39hRrMWs-Z2xrXXw
TELEGRAM_ALLOWED_USER_ID=7862672686
OPENCODE_API_URL=http://localhost:4096
OPENCODE_MODEL_PROVIDER=bai
OPENCODE_MODEL_ID=glm-5.3-flash
BOT_LOCALE=en
LOG_LEVEL=info
EEOF
fi
if ! command -v ccbot >/dev/null 2>&1; then
  pipx install git+https://github.com/six-ddc/ccbot.git || pip install git+https://github.com/six-ddc/ccbot.git
fi
ccbot hook --install 2>&1 | head -n 5 || true
if ! tmux has-session -t ccbot 2>/dev/null; then
  tmux new-session -d -s ccbot
  echo "tmux ccbot created"
else
  echo "tmux ccbot exists"
fi
if pgrep -f "ccbot" >/dev/null 2>&1; then
  echo "ccbot already running"
else
  nohup ccbot > /tmp/ccbot.log 2>&1 &
  echo "ccbot started pid $!"
fi
sleep 2
if [ -f /workspaces/agent-bayu/scripts/run-opencode-telegram.sh ]; then
  bash /workspaces/agent-bayu/scripts/run-opencode-telegram.sh || true
else
  echo "run-opencode-telegram.sh not found, starting serve directly"
  if ! ss -tlnp 2>/dev/null | grep -q "127.0.0.1:4096"; then
    nohup opencode serve --port 4096 > /tmp/opencode_serve.log 2>&1 &
    echo "opencode serve started"
  fi
  if ! opencode-telegram status 2>&1 | grep -q running; then
    opencode-telegram start --daemon 2>&1 | head -n 10 || true
  fi
fi
echo "[$(date -Iseconds)] autostart done auto-permissions: claude --dangerously-skip-permissions + opencode permission * allow"
