#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
deploy_dir="$project_root/deploy"
env_file="$deploy_dir/.env"

[ -f "$env_file" ] || { echo '缺少 deploy/.env，请先从 .env.example 复制并填写' >&2; exit 1; }
if grep -Eiq 'replace-with|your-server-ip-or-domain' "$env_file"; then
  echo 'deploy/.env 仍包含占位符，拒绝部署' >&2
  exit 1
fi

for key in \
  JOBPROOF_PUBLIC_ORIGIN \
  JOBPROOF_REDIS_PASSWORD \
  JOBPROOF_MINIO_ACCESS_KEY \
  JOBPROOF_MINIO_SECRET_KEY \
  JOBPROOF_AI_MASTER_KEY \
  JOBPROOF_CONTACT_HMAC_SECRET \
  JOBPROOF_EMAIL_CODE_HMAC_SECRET; do
  grep -Eq "^${key}=.+" "$env_file" || { echo "deploy/.env 缺少非空配置: $key" >&2; exit 1; }
done

command -v docker >/dev/null 2>&1 || { echo '缺少 Docker' >&2; exit 1; }
(cd "$deploy_dir" && docker compose config --quiet)
if grep -Eq '^JOBPROOF_DEV_ADMIN_ENABLED=true[[:space:]]*$' "$env_file"; then
  echo '警告：管理员一次性初始化器仍处于启用状态；初始化后必须关闭并清空密码' >&2
fi
echo 'deploy/.env 必填项、占位符和 Compose 语法检查通过。'
