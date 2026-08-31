#!/usr/bin/env bash
# Optional developer tools must never prevent a Codespace from being created.
set -u

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
ccbot_env="$repo_root/.env/ccbot.env"
telegram_env="$repo_root/.env/opencode-telegram.env"

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

copy_env() {
  local source="$1"
  local destination="$2"

  if [ -f "$source" ]; then
    mkdir -p "$(dirname "$destination")"
    cp "$source" "$destination"
    chmod 600 "$destination"
    echo "Copied $(basename "$source") to its user configuration."
  fi
}

copy_env "$ccbot_env" "$HOME/.ccbot/.env"
copy_env "$telegram_env" "$HOME/.config/opencode-telegram-bot/.env"

if [ ! -f "$ccbot_env" ] && [ -n "${TELEGRAM_BOT_TOKEN_CC:-}" ]; then
  umask 077
  mkdir -p "$HOME/.ccbot"
  printf 'TELEGRAM_BOT_TOKEN=%s\nALLOWED_USERS=%s\nTMUX_SESSION_NAME=ccbot\nCLAUDE_COMMAND=claude --dangerously-skip-permissions\n' \
    "$TELEGRAM_BOT_TOKEN_CC" "${ALLOWED_USERS:-7862672686}" >"$HOME/.ccbot/.env"
fi

load_nvm

if [ ! -f "$telegram_env" ] && [ -n "${TELEGRAM_BOT_TOKEN_OP:-}" ]; then
  umask 077
  mkdir -p "$HOME/.config/opencode-telegram-bot"
  printf 'TELEGRAM_BOT_TOKEN=%s\nTELEGRAM_ALLOWED_USER_ID=%s\nOPENCODE_API_URL=http://localhost:4096\nOPENCODE_MODEL_PROVIDER=bai\nOPENCODE_MODEL_ID=glm-5.3-flash\n' \
    "$TELEGRAM_BOT_TOKEN_OP" "${ALLOWED_USERS:-7862672686}" >"$HOME/.config/opencode-telegram-bot/.env"
fi

if ! command -v ccbot >/dev/null 2>&1; then
  if command -v pipx >/dev/null 2>&1; then
    pipx install git+https://github.com/six-ddc/ccbot.git || \
      echo "Warning: ccbot could not be installed; run the command again after the Codespace starts."
  else
    echo "Warning: pipx is unavailable; ccbot was not installed."
  fi
fi

install_npm_tool() {
  local command_name="$1"
  local package_name="$2"

  if ! command -v "$command_name" >/dev/null 2>&1; then
    npm install -g "$package_name" || \
      echo "Warning: $package_name could not be installed; run the command again after the Codespace starts."
  fi
}

if command -v npm >/dev/null 2>&1; then
  install_npm_tool claude @anthropic-ai/claude-code
  install_npm_tool opencode opencode-ai
  install_npm_tool opencode-telegram @grinev/opencode-telegram-bot
else
  echo "Warning: npm is unavailable; Claude Code, OpenCode, and its Telegram bridge were not installed."
fi

if command -v ccbot >/dev/null 2>&1; then
  ccbot hook --install || echo "Warning: ccbot hook installation failed."
fi

exit 0
