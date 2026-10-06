#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
run_dir="$project_root/.local-run"
state_file="$run_dir/run-state"

if [ ! -f "$state_file" ]; then
  echo '没有找到本项目的运行状态，未停止任何进程。'
  exit 0
fi

backend_pid=$(sed -n 's/^backendPid=//p' "$state_file")
frontend_pid=$(sed -n 's/^frontendPid=//p' "$state_file")

stop_tree() {
  process_id=$1
  expected_marker=$2
  case "$process_id" in ''|*[!0-9]*) echo "无效 PID: $process_id" >&2; return 1 ;; esac
  kill -0 "$process_id" 2>/dev/null || return 0
  command_line=$(ps -p "$process_id" -o command= 2>/dev/null || true)
  case "$command_line" in
    *"$expected_marker"*) ;;
    *) echo "PID $process_id 的命令与 $expected_marker 不符，拒绝停止" >&2; return 1 ;;
  esac
  if command -v pkill >/dev/null 2>&1; then pkill -TERM -P "$process_id" 2>/dev/null || true; fi
  kill "$process_id" 2>/dev/null || true
  attempt=0
  while kill -0 "$process_id" 2>/dev/null && [ "$attempt" -lt 20 ]; do
    sleep 0.25
    attempt=$((attempt + 1))
  done
  if kill -0 "$process_id" 2>/dev/null; then
    if command -v pkill >/dev/null 2>&1; then pkill -KILL -P "$process_id" 2>/dev/null || true; fi
    kill -KILL "$process_id" 2>/dev/null || true
  fi
}

stop_tree "$frontend_pid" 'npm run dev'
stop_tree "$backend_pid" 'jobproof-backend.jar'
rm -f -- "$state_file" "$run_dir/backend.pid" "$run_dir/frontend.pid"
echo 'JobProof AI 本地进程已停止。日志保留在 .local-run。'
