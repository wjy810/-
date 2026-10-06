#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
frontend_dir="$project_root/frontend"
backend_dir="$project_root/backend"
deploy_dir="$project_root/deploy"
dist_dir="$frontend_dir/dist"
web_dir="$deploy_dir/web"

for command_name in node npm java mvn; do
  command -v "$command_name" >/dev/null 2>&1 || { echo "缺少命令: $command_name" >&2; exit 1; }
done

node_version=$(node -p 'process.versions.node')
node -e "const [a,b]=process.versions.node.split('.').map(Number); process.exit(a === 24 && b >= 15 ? 0 : 1)" || {
  echo "需要 Node.js >=24.15.0 且 <25，当前为 $node_version" >&2
  exit 1
}

(cd "$frontend_dir" && npm ci && npm test && npm run build)
(cd "$backend_dir" && mvn -B verify)

[ -f "$dist_dir/index.html" ] || { echo '前端构建未产生 dist/index.html' >&2; exit 1; }
[ -f "$backend_dir/target/jobproof-backend.jar" ] || { echo '后端构建未产生目标 JAR' >&2; exit 1; }
case "$web_dir" in "$project_root/deploy/web") ;; *) echo 'deploy/web 路径校验失败' >&2; exit 1 ;; esac

rm -rf -- "$web_dir"
mkdir -p -- "$web_dir"
cp -R "$dist_dir"/. "$web_dir"/
echo '测试与构建通过，deploy/web 和 backend/target/jobproof-backend.jar 已就绪。'
