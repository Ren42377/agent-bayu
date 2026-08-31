tol#!/usr/bin/env bash
set -u

if pgrep -f '[c]cbot($| )' >/dev/null 2>&1; then
  pkill -f '[c]cbot($| )' || true
  echo "ccbot stopped"
else
  echo "ccbot not running"
fi

if pgrep -f '[o]pencode serve' >/dev/null 2>&1; then
  pkill -f '[o]pencode serve' || true
  echo "opencode serve stopped"
fi

if command -v opencode-telegram >/dev/null 2>&1; then
  opencode-telegram stop 2>&1 || true
fi
