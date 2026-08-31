#!/usr/bin/env bash
set -u

config_file="$HOME/.config/opencode-telegram-bot/.env"
serve_log="/tmp/opencode_serve.log"
bot_log="/tmp/telegram_autostart.log"
port=4096
api_url="http://127.0.0.1:${port}"
lock="/tmp/run-opencode-telegram.lock"

exec 9>"$lock"
if ! flock -n 9; then
  echo "another autostart is running"
  exit 0
fi

if [ ! -f "$config_file" ]; then
  echo "missing $config_file"
  exit 0
fi

if ! command -v opencode >/dev/null 2>&1; then
  echo "opencode is not installed"
  exit 0
fi

if ! curl --connect-timeout 1 --max-time 2 -fsS "$api_url" >/dev/null 2>&1; then
  nohup opencode serve --port "$port" >"$serve_log" 2>&1 &
  echo "started opencode serve; log: $serve_log"
fi

if ! command -v opencode-telegram >/dev/null 2>&1; then
  echo "opencode-telegram is not installed"
  exit 0
fi

if opencode-telegram status 2>&1 | grep -q "running"; then
  echo "telegram already running"
else
  opencode-telegram start --daemon >>"$bot_log" 2>&1 || \
    echo "opencode-telegram could not start; log: $bot_log"
fi
