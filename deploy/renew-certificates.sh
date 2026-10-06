#!/usr/bin/env sh
set -eu
root_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$root_dir"
: "${JOBPROOF_HTTPS_ORIGIN:?set https public origin}"
docker compose -f docker-compose.yml -f docker-compose.https.yml run --rm --no-deps certbot renew --non-interactive "$@"
# Reload on successful renewal invocation (safe even when no certificate was due).
docker compose -f docker-compose.yml -f docker-compose.https.yml exec -T nginx nginx -t
docker compose -f docker-compose.yml -f docker-compose.https.yml exec -T nginx nginx -s reload
