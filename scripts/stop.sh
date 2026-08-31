#!/bin/bash
set -e
if pgrep -f "ccbot" >/dev/null 2>&1; then pkill -f "ccbot" || true; echo "ccbot stopped"; else echo "ccbot not running"; fi
if pgrep -f "opencode serve" >/dev/null 2>&1; then pkill -f "opencode serve" || true; echo "opencode serve stopped"; fi
opencode-telegram stop 2>&1 || true
