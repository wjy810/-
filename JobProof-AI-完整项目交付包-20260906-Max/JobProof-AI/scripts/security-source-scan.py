"""Bounded credential regression scan. Never print a matched credential value."""
import argparse
import pathlib
import re
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
PATTERNS = {
    "private-key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "aliyun-key": re.compile(rb"\bLTAI[A-Za-z0-9]{16,}\b"),
    "aws-key": re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "provider-token": re.compile(rb"\b(?:sk-[A-Za-z0-9_-]{24,}|gh[pousr]_[A-Za-z0-9]{30,})\b"),
    "credential-literal": re.compile(
        rb"(?i)(?:password|secret|api[_-]?key|auth[_-]?code|access[_-]?key)[A-Za-z0-9_-]*"
        rb"\s*(?:=|:)\s*[\"']([A-Za-z0-9+/=_-]{16,})[\"']"
    ),
    "credential-bare": re.compile(
        rb"(?im)(?:password|secret|api[_-]?key|auth[_-]?code|access[_-]?key)[A-Za-z0-9_-]*"
        rb"\s*(?:=|:)\s*([A-Za-z0-9+/=_-]{16,})\s*(?:#.*)?$"
    ),
}
PLACEHOLDERS = re.compile(
    rb"(?i)(replace-with|synthetic-drill|not-a-real|example|placeholder|changeme|test-secret|jobproof[-_](?:dev|local))"
)
DENIED_CLASS = "com/jobproof/infrastructure/config/DevVerificationProviderConfiguration.class"
CONFIG_SUFFIXES = (".conf", ".properties", ".yaml", ".yml")


def findings(name, data):
    result = []
    for rule, pattern in PATTERNS.items():
        if rule == "credential-bare" and not name.lower().endswith(CONFIG_SUFFIXES):
            continue
        for match in pattern.finditer(data):
            if PLACEHOLDERS.search(match.group()):
                continue
            line_start = data.rfind(b"\n", 0, match.start()) + 1
            line_end = data.find(b"\n", match.end())
            if line_end < 0:
                line_end = len(data)
            if b"${" in data[line_start:match.start()] and b"}" in data[match.end():line_end]:
                continue
            line = data[:match.start()].count(b"\n") + 1
            result.append(f"{name}:{line}: {rule} [REDACTED]")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=pathlib.Path)
    args = parser.parse_args()
    failures = []
    count = 0
    if args.jar:
        if not args.jar.is_file():
            raise SystemExit("Required production JAR does not exist")
        with zipfile.ZipFile(args.jar) as jar:
            for entry in jar.infolist():
                if not entry.filename.startswith("BOOT-INF/classes/") or entry.is_dir():
                    continue
                count += 1
                if entry.filename.endswith(DENIED_CLASS):
                    failures.append(f"{entry.filename}: forbidden embedded credential class")
                failures.extend(findings(entry.filename, jar.read(entry)))
    else:
        targets = [ROOT / p for p in ("backend/src/main", "frontend/src", "frontend/scripts", "scripts", "deploy", ".github")]
        for target in targets:
            for path in target.rglob("*"):
                relative = path.relative_to(ROOT)
                if not path.is_file() or any(part in {"data", "web", "backups", "letsencrypt", "acme-webroot", "acme-logs", "__pycache__"} for part in relative.parts):
                    continue
                if path.name == ".env" or path.suffix not in {".java", ".properties", ".yaml", ".yml", ".ts", ".js", ".mjs", ".vue", ".sh", ".py", ".conf"}:
                    continue
                count += 1
                failures.extend(findings(str(relative), path.read_bytes()))
    for failure in failures:
        print(failure)
    print(f"Scanned {count} production/source entries; findings={len(failures)}; values always redacted")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
