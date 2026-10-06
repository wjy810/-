#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
backend_dir="$project_root/backend"
frontend_dir="$project_root/frontend"
run_dir="$project_root/.local-run"
state_file="$run_dir/run-state"
jar_file="$backend_dir/target/jobproof-backend.jar"
rebuild=0
if [ "${1:-}" = '--rebuild' ]; then rebuild=1; shift; fi
[ "$#" -eq 0 ] || { echo '用法: scripts/start-local.sh [--rebuild]' >&2; exit 1; }

for command_name in java node npm curl; do
  command -v "$command_name" >/dev/null 2>&1 || { echo "缺少命令: $command_name" >&2; exit 1; }
done

node_version=$(node -p 'process.versions.node')
node -e "const [a,b]=process.versions.node.split('.').map(Number); process.exit(a === 24 && b >= 15 ? 0 : 1)" || {
  echo "需要 Node.js >=24.15.0 且 <25，当前为 $node_version" >&2
  exit 1
}

[ ! -f "$state_file" ] || { echo "发现 $state_file，请先运行 scripts/stop-local.sh" >&2; exit 1; }

port_is_free() {
  if command -v lsof >/dev/null 2>&1; then
    ! lsof -n -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
  elif command -v ss >/dev/null 2>&1; then
    ! ss -ltn | awk '{print $4}' | grep -Eq "(^|:)$1$"
  else
    return 0
  fi
}
port_is_free 18081 || { echo '端口 18081 已被占用' >&2; exit 1; }
port_is_free 5174 || { echo '端口 5174 已被占用' >&2; exit 1; }

if [ "$rebuild" -eq 1 ] || [ ! -f "$jar_file" ]; then
  command -v mvn >/dev/null 2>&1 || { echo '缺少 Maven，且交付 JAR 不存在' >&2; exit 1; }
  (cd "$backend_dir" && mvn -B verify)
fi

if [ "$rebuild" -eq 1 ] || [ ! -d "$frontend_dir/node_modules" ]; then
  (cd "$frontend_dir" && npm ci)
fi

mkdir -p "$run_dir"
backend_pid=''
frontend_pid=''
cleanup_started() {
  [ -z "$frontend_pid" ] || kill "$frontend_pid" 2>/dev/null || true
  [ -z "$backend_pid" ] || kill "$backend_pid" 2>/dev/null || true
  rm -f -- "$state_file"
}
trap cleanup_started INT TERM HUP

(cd "$backend_dir" && nohup java -jar "$jar_file" \
  --spring.profiles.active=dev \
  --server.port=18081 \
  --jobproof.cors.origins=http://127.0.0.1:5174 \
  --jobproof.storage.type=local \
  --jobproof.storage.local-dir="$backend_dir/.local-data/files" \
  --jobproof.verification.email-provider=dev \
  --jobproof.verification.sms-provider=dev \
  --jobproof.dev.admin.enabled=true \
  --jobproof.dev.admin.alias=admin \
  --jobproof.dev.admin.email=admin@jobproof.local \
  --jobproof.dev.admin.password=admin \
  --jobproof.dev.seeker.enabled=true \
  --jobproof.dev.seeker.alias=seeker \
  --jobproof.dev.seeker.email=seeker@jobproof.local \
  --jobproof.dev.seeker.password=seeker123 \
  >"$run_dir/backend.log" 2>&1 & echo $! >"$run_dir/backend.pid")
backend_pid=$(cat "$run_dir/backend.pid")

(cd "$frontend_dir" && nohup npm run dev -- --host 127.0.0.1 --port 5174 \
  >"$run_dir/frontend.log" 2>&1 & echo $! >"$run_dir/frontend.pid")
frontend_pid=$(cat "$run_dir/frontend.pid")
printf 'backendPid=%s\nfrontendPid=%s\n' "$backend_pid" "$frontend_pid" >"$state_file"

ready=0
attempt=1
while [ "$attempt" -le 90 ]; do
  kill -0 "$backend_pid" 2>/dev/null || { echo "后端提前退出，请查看 $run_dir/backend.log" >&2; cleanup_started; exit 1; }
  kill -0 "$frontend_pid" 2>/dev/null || { echo "前端提前退出，请查看 $run_dir/frontend.log" >&2; cleanup_started; exit 1; }
  if curl -fsS http://127.0.0.1:18081/api/v1/health 2>/dev/null | grep -q '"status":"UP"' && \
     curl -fsS http://127.0.0.1:5174/ >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
  attempt=$((attempt + 1))
done

[ "$ready" -eq 1 ] || { echo "服务未在 180 秒内就绪，请查看 $run_dir 下的日志" >&2; cleanup_started; exit 1; }
trap - INT TERM HUP
echo 'JobProof AI 已启动: http://127.0.0.1:5174/'
echo '管理员: admin / admin（仅本地开发）'
echo '停止命令: ./scripts/stop-local.sh'
