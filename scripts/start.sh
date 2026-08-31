#!/bin/bash
set -e
export PATH="$HOME/.bun/bin:$HOME/.local/bin:$HOME/.opencode/bin:/usr/local/py-utils/bin:$PATH"
if ! tmux has-session -t ccbot 2>/dev/null; then tmux new-session -d -s ccbot; echo "tmux ccbot created"; fi
if pgrep -f "ccbot" >/dev/null 2>&1; then echo "ccbot already running auto"; else nohup ccbot > /tmp/ccbot.log 2>&1 & echo "ccbot started auto"; sleep 2; fi
bash /workspaces/agent-bayu/scripts/run-opencode-telegram.sh
