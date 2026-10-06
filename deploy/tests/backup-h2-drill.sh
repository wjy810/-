#!/usr/bin/env sh
# Uses only new synthetic Compose projects. Keeps resources for reviewed cleanup.
set -eu
umask 077
command -v timeout >/dev/null
docker() { command timeout 180 docker "$@"; }
test_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
jar=${1:?provide the verified application jar}
clamav=${2:?provide the isolated healthy ClamAV container name}
case "$clamav" in jobproof-clamav*-validation-*) ;; *) echo 'Expected isolated validation ClamAV' >&2; exit 1 ;; esac
[ "$(docker inspect --format '{{.State.Health.Status}}' "$clamav")" = healthy ]
[ -f "$jar" ]
fixture=$(mktemp -d /var/tmp/jobproof-h2drill.XXXXXX)
drill="jobproof-h2drill-$(date +%Y%m%d%H%M%S)-$$"
source_project="$drill-source"
restored_project="$drill-restored"
printf 'ISOLATED H2 ROOT=%s\n' "$fixture"
mkdir "$fixture/source" "$fixture/source/data" "$fixture/restored" "$fixture/restored/data" "$fixture/unpacked"
cp "$jar" "$fixture/jobproof-backend.jar"
cp "$test_dir/h2-drill-compose.yml" "$fixture/source/docker-compose.yml"
cp "$test_dir/h2-drill-compose.yml" "$fixture/restored/docker-compose.yml"
cp "$test_dir/../backup.sh" "$fixture/source/backup.sh"
printf 'fixture=%s\nsource_project=%s\nrestored_project=%s\n' "$fixture" "$source_project" "$restored_project" > "$fixture/resources.txt"
sha256sum "$fixture/jobproof-backend.jar" > "$fixture/jar-sha256.txt"
compose() { docker compose -p "$JOBPROOF_DRILL_PROJECT" -f "$fixture/$1/docker-compose.yml" "$2" ${3-}; }
run_fixture() {
    docker run --rm --network "${JOBPROOF_DRILL_PROJECT}_default" --mount "type=bind,src=$test_dir,dst=/tests,readonly" --mount "type=bind,src=$fixture,dst=/fixture" python:3.11-alpine python /tests/h2-drill-fixture.py "$1" /fixture
}
stop_owned() {
    result=$?
    trap - 0 HUP INT TERM
    set +e
    command timeout 20 docker compose -p "$source_project" -f "$fixture/source/docker-compose.yml" logs --no-color app > "$fixture/source-app.log" 2>&1
    command timeout 20 docker compose -p "$restored_project" -f "$fixture/restored/docker-compose.yml" logs --no-color app > "$fixture/restored-app.log" 2>&1
    if ! command timeout 25 docker compose -p "$source_project" -f "$fixture/source/docker-compose.yml" stop > "$fixture/source-stop.log" 2>&1; then result=1; fi
    if ! command timeout 25 docker compose -p "$restored_project" -f "$fixture/restored/docker-compose.yml" stop > "$fixture/restored-stop.log" 2>&1; then result=1; fi
    printf 'exit_code=%s\n' "$result" > "$fixture/drill-exit.txt"
    exit "$result"
}
trap stop_owned 0
trap 'exit 130' INT
trap 'exit 143' TERM
export JOBPROOF_DRILL_PROJECT="$source_project"
compose source create
docker network connect --alias clamav "${source_project}_default" "$clamav"
compose source up -d
run_fixture wait
run_fixture create
sh "$fixture/source/backup.sh"
compose source stop
archive=$(find "$fixture/source/backups" -maxdepth 1 -type f -name 'jobproof-*.tar.gz')
tar -tzf "$archive" > "$fixture/outer-members.txt"
[ "$(wc -l < "$fixture/outer-members.txt")" -eq 4 ]
for member in manifest.txt SHA256SUMS app-data.tar.gz minio-data.tar.gz; do grep -qx "$member" "$fixture/outer-members.txt"; done
tar -xzf "$archive" -C "$fixture/unpacked"
(cd "$fixture/unpacked" && sha256sum -c SHA256SUMS) > "$fixture/checksums.log"
docker run --rm --network none --mount "type=bind,src=$test_dir,dst=/tests,readonly" --mount "type=bind,src=$fixture/unpacked,dst=/fixture,readonly" python:3.11-alpine python /tests/drill-fixture.py validate-tar /fixture
tar -xzf "$fixture/unpacked/app-data.tar.gz" -C "$fixture/restored/data"
[ -s "$fixture/restored/data/jobproof.mv.db" ]
sha256sum "$fixture/restored/data/jobproof.mv.db" > "$fixture/restored-h2-before-start.sha256"
export JOBPROOF_DRILL_PROJECT="$restored_project"
compose restored create
restored_id=$(docker compose -f "$fixture/restored/docker-compose.yml" ps -a -q minio)
restored_volume=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}{{end}}{{end}}' "$restored_id")
case "$restored_volume" in "$restored_project"_objects) ;; *) echo 'Unexpected restored volume' >&2; exit 1 ;; esac
docker run --rm --network none --mount "type=volume,src=$restored_volume,dst=/restore" --mount "type=bind,src=$fixture/unpacked,dst=/backup,readonly" --entrypoint sh redis:7.4-alpine -c 'test -z "$(ls -A /restore)" && tar -xzf /backup/minio-data.tar.gz -C /restore'
compose restored up -d
run_fixture wait
run_fixture verify
docker compose -p "$source_project" -f "$fixture/source/docker-compose.yml" logs --no-color app > "$fixture/source-app.log"
docker compose -p "$restored_project" -f "$fixture/restored/docker-compose.yml" logs --no-color app > "$fixture/restored-app.log"
printf 'archive=%s\nrestored_volume=%s\n' "$archive" "$restored_volume" >> "$fixture/resources.txt"
printf 'PASS real application H2 backup/restore; evidence preserved: %s\n' "$fixture"
