#!/usr/bin/env bash
# Lifecycle commands run on every start. Keep failures here non-fatal so
# Codespaces can always open the normal container rather than recovery mode.
set -u

export PATH="$HOME/.bun/bin:$HOME/.local/bin:$HOME/.opencode/bin:/usr/local/py-utils/bin:$PATH"

load_nvm() {
  local nvm_script=""

  if [ -n "${NVM_DIR:-}" ] && [ -s "$NVM_DIR/nvm.sh" ]; then
    nvm_script="$NVM_DIR/nvm.sh"
  elif [ -s /usr/local/share/nvm/nvm.sh ]; then
    nvm_script="/usr/local/share/nvm/nvm.sh"
  fi

  if [ -n "$nvm_script" ]; then
    set +u
    # shellcheck source=/dev/null
    . "$nvm_script"
    set -u
  fi
}

load_nvm

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
ccbot_env="$repo_root/.env/ccbot.env"
telegram_env="$repo_root/.env/opencode-telegram.env"
port=4096

copy_env() {
  local source="$1"
  local destination="$2"

  if [ -f "$source" ]; then
    mkdir -p "$(dirname "$destination")"
    cp "$source" "$destination"
    chmod 600 "$destination"
  fi
}

copy_env "$ccbot_env" "$HOME/.ccbot/.env"
copy_env "$telegram_env" "$HOME/.config/opencode-telegram-bot/.env"

if command -v opencode >/dev/null 2>&1; then
  if ! curl --connect-timeout 1 --max-time 2 -fsS "http://127.0.0.1:$port" >/dev/null 2>&1; then
    nohup opencode serve --port "$port" >/tmp/opencode_serve.log 2>&1 &
    echo "Started OpenCode server; log: /tmp/opencode_serve.log"
  fi
else
  echo "OpenCode is unavailable; skipping its server startup."
fi

if command -v ccbot >/dev/null 2>&1; then
  if command -v tmux >/dev/null 2>&1 && ! tmux has-session -t ccbot 2>/dev/null; then
    tmux new-session -d -s ccbot || echo "Warning: could not create the ccbot tmux session."
  fi

  # The bracket prevents pgrep from matching this lifecycle shell itself.
  if ! pgrep -f '[c]cbot($| )' >/dev/null 2>&1; then
    nohup ccbot >/tmp/ccbot.log 2>&1 &
    echo "Started ccbot; log: /tmp/ccbot.log"
  fi
else
  echo "ccbot is unavailable; skipping its startup."
fi

if command -v opencode-telegram >/dev/null 2>&1 && [ -f "$HOME/.config/opencode-telegram-bot/.env" ]; then
  if ! opencode-telegram status 2>&1 | grep -q 'running'; then
    opencode-telegram start --daemon >/tmp/opencode_telegram.log 2>&1 || \
      echo "Warning: opencode-telegram did not start; log: /tmp/opencode_telegram.log"
  fi
fi

# Do not call Telegram's getUpdates here: acknowledging pending messages during
# container startup discards work, and network trouble must not fail setup.
exit 0
