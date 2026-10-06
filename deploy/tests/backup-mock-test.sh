#!/usr/bin/env sh
# No Docker daemon is contacted: PATH injects the adjacent fake Docker executable.
set -eu
test_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
fixture=$(mktemp -d "${TMPDIR:-/tmp}/jobproof-backup-test.XXXXXX")
cleanup() {
    case "$fixture" in "${TMPDIR:-/tmp}"/jobproof-backup-test.*) rm -rf -- "$fixture" ;; esac
}
trap cleanup 0
mkdir -p "$fixture/deploy/data" "$fixture/bin" "$fixture/volume"
cp "$test_dir/../backup.sh" "$fixture/deploy/backup.sh"
cp "$test_dir/fake-docker.sh" "$fixture/bin/docker"
chmod +x "$fixture/bin/docker"
touch "$fixture/deploy/docker-compose.yml" "$fixture/deploy/data/database-fixture" "$fixture/volume/object-fixture"
export PATH="$fixture/bin:$PATH" MOCK_ROOT="$fixture" MOCK_LOG="$fixture/docker.log"

run_case() {
    export MOCK_FAIL="$1" MOCK_APP_STATE="$2" MOCK_MINIO_STATE="$3"
    : > "$MOCK_LOG"
    if sh "$fixture/deploy/backup.sh" > "$fixture/output" 2>&1; then result=0; else result=$?; fi
}

run_case none running running
test "$result" = 0
archive=$(find "$fixture/deploy/backups" -name 'jobproof-*.tar.gz' -type f | head -n 1)
tar -tzf "$archive" | grep -q '^minio-data.tar.gz$'
grep -q '^start aa$' "$MOCK_LOG"
grep -q '^start bb$' "$MOCK_LOG"
success_count=$(find "$fixture/deploy/backups" -name 'jobproof-*.tar.gz' | wc -l)

run_case archive running running
test "$result" != 0
grep -q '^start aa$' "$MOCK_LOG"
grep -q '^start bb$' "$MOCK_LOG"
test "$(find "$fixture/deploy/backups" -name 'jobproof-*.tar.gz' | wc -l)" = "$success_count"

run_case missing-volume running running
test "$result" != 0
! grep -q '^stop ' "$MOCK_LOG"

run_case none exited exited
test "$result" = 0
! grep -q '^start ' "$MOCK_LOG"
! grep -q '^stop ' "$MOCK_LOG"
printf '%s\n' 'PASS: complete archive, failure restart/no publish, missing-volume preflight, originally stopped states'
