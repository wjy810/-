#!/usr/bin/env sh
# Leaves isolated synthetic resources for explicit reviewed cleanup. Never targets production.
set -eu
umask 077
test_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
fixture=$(mktemp -d "${TMPDIR:-/tmp}/jobproof-auditdrill.XXXXXX")
drill="jobproof-auditdrill-$(date +%Y%m%d%H%M%S)-$$"
export JOBPROOF_DRILL_PROJECT="$drill-source"
printf 'ISOLATED ROOT=%s\nSOURCE PROJECT=%s\nRESTORE PROJECT=%s-restored\n' "$fixture" "$JOBPROOF_DRILL_PROJECT" "$drill"
mkdir "$fixture/source" "$fixture/source/data" "$fixture/restored" "$fixture/restored/data" "$fixture/restored/downloads" "$fixture/unpacked"
cp "$test_dir/drill-compose.yml" "$fixture/source/docker-compose.yml"
cp "$test_dir/drill-compose.yml" "$fixture/restored/docker-compose.yml"
cp "$test_dir/../backup.sh" "$fixture/source/backup.sh"
printf 'fixture=%s\nsource_project=%s\nrestored_project=%s-restored\n' "$fixture" "$JOBPROOF_DRILL_PROJECT" "$drill" > "$fixture/resources.txt"
docker run --rm --network none --mount "type=bind,src=$test_dir,dst=/tests,readonly" --mount "type=bind,src=$fixture/source/data,dst=/fixture" python:3.11-alpine python /tests/drill-fixture.py create /fixture
docker compose -f "$fixture/source/docker-compose.yml" up -d
mc() {
    docker run --rm --network "${JOBPROOF_DRILL_PROJECT}_default" -e MC_HOST_drill=http://synthetic-drill:synthetic-drill-password-not-a-real-credential@minio:9000 --mount "type=bind,src=$fixture,dst=/fixture" minio/mc:latest "$@"
}
attempt=0
until mc ready drill >/dev/null 2>&1; do attempt=$((attempt + 1)); [ "$attempt" -lt 30 ] || exit 1; sleep 1; done
mc mb drill/fixtures
mc cp /fixture/source/data/objects/resume.txt drill/fixtures/resume.txt
mc cp /fixture/source/data/objects/attachment.bin drill/fixtures/attachment.bin
start=$(date +%s)
sh "$fixture/source/backup.sh"
archive=$(find "$fixture/source/backups" -maxdepth 1 -name 'jobproof-*.tar.gz' -type f)
tar -tzf "$archive" > "$fixture/outer-members.txt"
[ "$(wc -l < "$fixture/outer-members.txt")" -eq 4 ]
for member in manifest.txt SHA256SUMS app-data.tar.gz minio-data.tar.gz; do grep -qx "$member" "$fixture/outer-members.txt"; done
tar -xzf "$archive" -C "$fixture/unpacked"
(cd "$fixture/unpacked" && sha256sum -c SHA256SUMS)
docker run --rm --network none --mount "type=bind,src=$test_dir,dst=/tests,readonly" --mount "type=bind,src=$fixture/unpacked,dst=/fixture,readonly" python:3.11-alpine python /tests/drill-fixture.py validate-tar /fixture
tar -xzf "$fixture/unpacked/app-data.tar.gz" -C "$fixture/restored/data"
export JOBPROOF_DRILL_PROJECT="$drill-restored"
docker compose -f "$fixture/restored/docker-compose.yml" create
restored_id=$(docker compose -f "$fixture/restored/docker-compose.yml" ps -a -q minio)
restored_volume=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}{{end}}{{end}}' "$restored_id")
case "$restored_volume" in "$drill-restored_objects") ;; *) echo 'Unexpected restore volume' >&2; exit 1 ;; esac
printf 'restored_volume=%s\n' "$restored_volume" >> "$fixture/resources.txt"
docker run --rm --network none --mount "type=volume,src=$restored_volume,dst=/restore" --mount "type=bind,src=$fixture/unpacked,dst=/backup,readonly" --entrypoint sh redis:7.4-alpine -c 'test -z "$(ls -A /restore)" && tar -xzf /backup/minio-data.tar.gz -C /restore'
docker compose -f "$fixture/restored/docker-compose.yml" start
attempt=0
until mc ready drill >/dev/null 2>&1; do attempt=$((attempt + 1)); [ "$attempt" -lt 30 ] || exit 1; sleep 1; done
mc cp drill/fixtures/resume.txt /fixture/restored/downloads/resume.txt
mc cp drill/fixtures/attachment.bin /fixture/restored/downloads/attachment.bin
if mc stat drill/fixtures/deliberately-missing.txt > "$fixture/missing-reference.txt" 2>&1; then echo 'Negative reference check failed' >&2; exit 1; fi
cmp "$fixture/source/data/references.sqlite" "$fixture/restored/data/references.sqlite"
docker run --rm --network none --mount "type=bind,src=$test_dir,dst=/tests,readonly" --mount "type=bind,src=$fixture/restored,dst=/fixture,readonly" python:3.11-alpine python /tests/drill-fixture.py verify /fixture | tee "$fixture/result.json"
printf 'elapsed_seconds=%s\narchive=%s\n' "$(( $(date +%s) - start ))" "$archive" >> "$fixture/resources.txt"
docker compose -f "$fixture/restored/docker-compose.yml" stop
export JOBPROOF_DRILL_PROJECT="$drill-source"
docker compose -f "$fixture/source/docker-compose.yml" stop
printf 'PASS real Docker backup/restore fixture; evidence preserved: %s\n' "$fixture"
