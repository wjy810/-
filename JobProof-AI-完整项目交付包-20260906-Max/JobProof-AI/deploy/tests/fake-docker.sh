#!/usr/bin/env sh
set -eu
printf '%s\n' "$*" >> "$MOCK_LOG"
case "$1" in
    compose)
        case "$*" in *'ps -a -q app') printf 'aa\n' ;; *'ps -a -q minio') printf 'bb\n' ;; esac
        ;;
    inspect)
        case "$3" in
            *'/var/lib/jobproof'*) printf '%s/deploy/data\n' "$MOCK_ROOT" ;;
            *'/data'*) test "$MOCK_FAIL" = missing-volume || printf 'fixture-minio-data\n' ;;
            *'State.Status'*) case "$4" in aa) printf '%s\n' "$MOCK_APP_STATE" ;; bb) printf '%s\n' "$MOCK_MINIO_STATE" ;; esac ;;
        esac
        ;;
    run)
        case "$*" in
            *'tar -C /source'*)
                test "$MOCK_FAIL" != archive || exit 9
                tar -C "$MOCK_ROOT/volume" -czf - .
                ;;
        esac
        ;;
    image|volume|pull|stop|start) : ;;
    *) exit 98 ;;
esac
