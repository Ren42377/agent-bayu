#!/usr/bin/env bash
# Kept as a manual entry point; Codespaces uses .devcontainer/post-start.sh.
set -u

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
exec bash "$repo_root/.devcontainer/post-start.sh"
