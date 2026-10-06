"""Synthetic SQLite references only; this is NOT the application's H2 schema."""
import hashlib
import json
import pathlib
import sqlite3
import sys

root = pathlib.Path(sys.argv[2])
if sys.argv[1] == "create":
    (root / "objects").mkdir()
    db = sqlite3.connect(root / "references.sqlite")
    db.execute("CREATE TABLE objects (object_key TEXT PRIMARY KEY, sha256 TEXT NOT NULL, expected_missing INTEGER NOT NULL)")
    for name, payload in [("resume.txt", b"SYNTHETIC RESUME fixture 2026\n"), ("attachment.bin", bytes(range(256)) * 32)]:
        (root / "objects" / name).write_bytes(payload)
        db.execute("INSERT INTO objects VALUES (?, ?, 0)", (name, hashlib.sha256(payload).hexdigest()))
    db.execute("INSERT INTO objects VALUES ('deliberately-missing.txt', 'not-present', 1)")
    db.commit()
    db.close()
elif sys.argv[1] == "validate-tar":
    import tarfile
    for archive in root.glob("*.tar.gz"):
        with tarfile.open(archive) as stream:
            for member in stream.getmembers():
                path = pathlib.PurePosixPath(member.name)
                if path.is_absolute() or ".." in path.parts or member.issym() or member.islnk() or member.isdev():
                    raise ValueError("Unsafe archive member")
elif sys.argv[1] == "verify":
    db = sqlite3.connect(f"file:{root / 'data/references.sqlite'}?mode=ro", uri=True)
    assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    rows = db.execute("SELECT object_key, sha256, expected_missing FROM objects ORDER BY object_key").fetchall()
    verified = []
    for key, expected, missing in rows:
        path = root / "downloads" / key
        if missing:
            assert not path.exists(), "Deliberately missing reference unexpectedly exists"
        else:
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            assert digest == expected, "Restored object hash mismatch"
            verified.append({"key": key, "sha256": digest, "bytes": path.stat().st_size})
    assert len(verified) == 2 and len(rows) == 3
    print(json.dumps({"status": "PASS", "database": "synthetic SQLite; NOT production H2/migrations", "integrity_check": "ok", "verified_objects": verified, "missing_reference_detected": True}, indent=2))
else:
    raise SystemExit("Unsupported fixture command")
