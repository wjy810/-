"""Exercise the real application on an isolated Compose network using synthetic data."""
import hashlib
import http.cookiejar
import json
import pathlib
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zlib

root = pathlib.Path(sys.argv[2])
base = "http://app:8080"
password = "Synthetic-Drill-Only-2026!"


def client():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def request(opener, path, method="GET", data=None, raw=False, content_type="application/json"):
    payload = data if isinstance(data, bytes) else json.dumps(data).encode() if data is not None else None
    result = opener.open(urllib.request.Request(base + path, payload, {"Content-Type": content_type}, method=method), timeout=30)
    body = result.read()
    return body if raw else json.loads(body)["data"]


def png(red):
    def chunk(kind, data):
        return struct.pack("!I", len(data)) + kind + data + struct.pack("!I", zlib.crc32(kind + data))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack("!IIBBBBB", 16, 16, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress((b"\0" + bytes([red, 80, 160]) * 16) * 16)) + chunk(b"IEND", b"")


def denied(opener, path):
    try:
        request(opener, path)
    except urllib.error.HTTPError as error:
        assert error.code in (403, 404), f"Unexpected isolation status {error.code}"
        return error.code
    raise AssertionError("Cross-account resource was readable")


if sys.argv[1] == "wait":
    for attempt in range(120):
        try:
            with urllib.request.urlopen(base + "/actuator/health", timeout=3) as response:
                assert json.load(response)["status"] == "UP"
            print("Application health UP")
            break
        except (OSError, AssertionError):
            time.sleep(1)
    else:
        raise SystemExit("Application did not become healthy")
elif sys.argv[1] == "create":
    accounts = []
    for index in range(2):
        opener = client()
        email = f"h2-backup-{index}@example.com"
        challenge = request(opener, "/api/v1/auth/verifications/request", "POST", {"channel": "EMAIL", "destination": email, "purpose": "REGISTER"})
        code = request(opener, "/internal/dev/mailbox/" + urllib.parse.quote(email))["code"]
        token = request(opener, "/api/v1/auth/verifications/confirm", "POST", {"challengeId": challenge["challengeId"], "destination": email, "code": code})["verificationToken"]
        account = request(opener, "/api/v1/auth/register", "POST", {"channel": "EMAIL", "destination": email, "verificationToken": token, "password": password, "acceptedTerms": True, "acceptedPrivacy": True})
        resume = request(opener, "/api/v1/resumes", "POST", {"mode": "BLANK", "title": f"Synthetic H2 restore resume {index}"})
        image = png(40 + index * 40)
        boundary = "jobproof-isolated-drill-boundary"
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="synthetic-{index}.png"\r\nContent-Type: image/png\r\n\r\n'.encode() + image + f"\r\n--{boundary}--\r\n".encode())
        uploaded = request(opener, "/api/v1/career-library/files", "POST", body, content_type="multipart/form-data; boundary=" + boundary)
        file_id = uploaded["file"]["id"]
        for attempt in range(90):
            file = request(opener, "/api/v1/career-library/files/" + file_id)
            if file["processingStatus"] == "READY":
                break
            if file["processingStatus"] in ("SCAN_FAILED", "PREVIEW_FAILED", "INFECTED"):
                raise AssertionError("File processing failed: " + str(file.get("previewError")))
            time.sleep(1)
        assert file["scanStatus"] == "CLEAN" and file["previewStatus"] == "READY", file
        original = request(opener, f"/api/v1/career-library/files/{file_id}/download", raw=True)
        preview = request(opener, f"/api/v1/career-library/files/{file_id}/preview-pages/1", raw=True)
        assert original == image and preview.startswith(b"\x89PNG")
        accounts.append({"email": email, "accountId": account["id"], "resumeId": resume["id"], "resumeTitle": resume["title"], "fileId": file_id, "fileSha256": hashlib.sha256(original).hexdigest(), "previewSha256": hashlib.sha256(preview).hexdigest()})
    (root / "fixture.json").write_text(json.dumps(accounts, indent=2))
    print("Created two synthetic accounts, resumes and ClamAV-scanned MinIO files")
elif sys.argv[1] == "verify":
    accounts = json.loads((root / "fixture.json").read_text())
    checks = []
    for index, account in enumerate(accounts):
        opener = client()
        logged_in = request(opener, "/api/v1/auth/login", "POST", {"identifier": account["email"], "password": password})
        assert logged_in["id"] == account["accountId"]
        resume = request(opener, "/api/v1/resumes/" + account["resumeId"])
        assert resume["title"] == account["resumeTitle"]
        file_id = account["fileId"]
        file = request(opener, "/api/v1/career-library/files/" + file_id)
        assert file["scanStatus"] == "CLEAN" and file["previewStatus"] == "READY"
        original = request(opener, f"/api/v1/career-library/files/{file_id}/download", raw=True)
        preview = request(opener, f"/api/v1/career-library/files/{file_id}/preview-pages/1", raw=True)
        assert hashlib.sha256(original).hexdigest() == account["fileSha256"]
        assert hashlib.sha256(preview).hexdigest() == account["previewSha256"]
        other = accounts[1 - index]
        checks.append({"accountIndex": index, "login": "PASS", "resume": "PASS", "originalSha256": account["fileSha256"], "previewSha256": account["previewSha256"], "otherResumeStatus": denied(opener, "/api/v1/resumes/" + other["resumeId"]), "otherFileStatus": denied(opener, "/api/v1/career-library/files/" + other["fileId"] + "/download")})
    (root / "result.json").write_text(json.dumps({"status": "PASS", "database": "real application file H2 and Flyway", "syntheticAccounts": 2, "checks": checks}, indent=2))
    print("PASS restored H2 identities, resumes, MinIO originals/previews and cross-account denials")
else:
    raise SystemExit("Unknown operation")
