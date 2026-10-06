#!/usr/bin/env sh
set -eu
umask 077

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
backup_dir="$root_dir/backups"
timestamp=$(date +%Y%m%d-%H%M%S)
helper_image=${JOBPROOF_BACKUP_HELPER_IMAGE:-redis:7.4-alpine}
retention_days=${JOBPROOF_BACKUP_RETENTION_DAYS:-7}
case "$retention_days" in ''|*[!0-9]*) echo 'Invalid backup retention days' >&2; exit 1 ;; esac
mkdir -p "$backup_dir"
lock_dir="$backup_dir/.backup.lock"
mkdir "$lock_dir" 2>/dev/null || { echo 'Backup already running (or stale .backup.lock needs review)' >&2; exit 1; }
stage=''
app_restart=0
minio_restart=0

restore_services() {
    restore_failed=0
    if [ "$minio_restart" = 1 ]; then
        if docker start "$minio_id" >/dev/null; then minio_restart=0; else restore_failed=1; fi
    fi
    if [ "$app_restart" = 1 ]; then
        if docker start "$app_id" >/dev/null; then app_restart=0; else restore_failed=1; fi
    fi
    return "$restore_failed"
}

cleanup() {
    result=$?
    trap - 0 HUP INT TERM
    set +e
    if ! restore_services; then
        echo 'ERROR: service restart failed; inspect app and MinIO immediately' >&2
        result=1
    fi
    case "$stage" in "$backup_dir"/.staging-*) rm -rf -- "$stage" ;; esac
    rmdir "$lock_dir"
    exit "$result"
}
trap cleanup 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

command -v tar >/dev/null
command -v sha256sum >/dev/null
data_dir=$(CDPATH= cd -- "$root_dir/data" && pwd -P)
app_id=$(docker compose -f "$root_dir/docker-compose.yml" ps -a -q app)
minio_id=$(docker compose -f "$root_dir/docker-compose.yml" ps -a -q minio)
case "$app_id" in ''|*[!a-fA-F0-9]*) echo 'Expected exactly one existing app container' >&2; exit 1 ;; esac
case "$minio_id" in ''|*[!a-fA-F0-9]*) echo 'Expected exactly one existing MinIO container' >&2; exit 1 ;; esac
app_mount=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/jobproof"}}{{if eq .Type "bind"}}{{.Source}}{{end}}{{end}}{{end}}' "$app_id")
[ "$app_mount" = "$data_dir" ] || { echo 'App data mount differs from deploy/data; refusing incomplete backup' >&2; exit 1; }
minio_volume=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{if eq .Type "volume"}}{{.Name}}{{end}}{{end}}{{end}}' "$minio_id")
case "$minio_volume" in ''|*[!a-zA-Z0-9_.-]*) echo 'Missing or unsupported MinIO named volume at /data' >&2; exit 1 ;; esac
docker volume inspect "$minio_volume" >/dev/null
app_state=$(docker inspect --format '{{.State.Status}}' "$app_id")
minio_state=$(docker inspect --format '{{.State.Status}}' "$minio_id")
case "$app_state:$minio_state" in *paused*|*restarting*|*removing*|*dead*) echo 'Container state is not safe for backup' >&2; exit 1 ;; esac
case "$app_state" in running|exited|created) ;; *) echo 'Unsupported app state' >&2; exit 1 ;; esac
case "$minio_state" in running|exited|created) ;; *) echo 'Unsupported MinIO state' >&2; exit 1 ;; esac

# Resolve/download and validate the helper before taking either service offline.
docker image inspect "$helper_image" >/dev/null 2>&1 || docker pull "$helper_image" >/dev/null
docker run --rm --network none --read-only --entrypoint sh "$helper_image" -c 'command -v tar >/dev/null && command -v gzip >/dev/null'
stage=$(mktemp -d "$backup_dir/.staging-$timestamp-XXXXXX")
if [ "$app_state" = running ]; then app_restart=1; docker stop "$app_id" >/dev/null; fi
if [ "$minio_state" = running ]; then minio_restart=1; docker stop "$minio_id" >/dev/null; fi

tar -C "$data_dir" -czf "$stage/app-data.tar.gz" .
docker run --rm --network none --read-only \
    --mount "type=volume,src=$minio_volume,dst=/source,readonly" \
    --entrypoint sh "$helper_image" -c 'tar -C /source -czf - .' > "$stage/minio-data.tar.gz"
# The consistent capture is complete; compression/validation can finish online.
restore_services
tar -tzf "$stage/app-data.tar.gz" >/dev/null
tar -tzf "$stage/minio-data.tar.gz" >/dev/null
printf '%s\n' 'format=jobproof-backup-v2' "created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    "app_state=$app_state" "minio_state=$minio_state" "minio_volume=$minio_volume" \
    "helper_image=$helper_image" 'includes=app-data.tar.gz,minio-data.tar.gz' \
    'secrets=excluded; recover configuration from separately secured storage' > "$stage/manifest.txt"
(cd "$stage" && sha256sum app-data.tar.gz minio-data.tar.gz > SHA256SUMS)
tar -C "$stage" -czf "$stage/archive.tar.gz" manifest.txt SHA256SUMS app-data.tar.gz minio-data.tar.gz
tar -tzf "$stage/archive.tar.gz" >/dev/null
archive="$backup_dir/jobproof-$timestamp-$$.tar.gz"
[ ! -e "$archive" ] || { echo 'Backup destination already exists' >&2; exit 1; }
mv "$stage/archive.tar.gz" "$archive"

# Prune only after a complete archive has been atomically published.
find "$backup_dir" -maxdepth 1 -type f -name 'jobproof-*.tar.gz' -mtime "+$retention_days" -delete
printf 'Backup complete: %s\n' "$archive"
