"""Inventory embedded Maven license declarations; unknowns require manual review."""
import argparse
import io
import json
import pathlib
import xml.etree.ElementTree as ET
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("jar", type=pathlib.Path)
parser.add_argument("output", type=pathlib.Path)
args = parser.parse_args()
packages = []
with zipfile.ZipFile(args.jar) as application:
    for item in application.namelist():
        if not item.startswith("BOOT-INF/lib/") or not item.endswith(".jar"):
            continue
        licenses = set()
        with zipfile.ZipFile(io.BytesIO(application.read(item))) as library:
            for name in library.namelist():
                if name.startswith("META-INF/maven/") and name.endswith("/pom.xml"):
                    pom = ET.fromstring(library.read(name))
                    for license_node in pom.findall("{*}licenses/{*}license"):
                        declared = license_node.findtext("{*}name") or license_node.findtext("{*}url")
                        if declared:
                            licenses.add(declared.strip())
        packages.append({"artifact": item.removeprefix("BOOT-INF/lib/"), "declared_licenses": sorted(licenses), "review_required": not licenses})
result = {"scope": "Embedded dependency POM declarations only; parent POM inheritance and license compatibility NOT adjudicated", "artifact": args.jar.name, "packages": packages, "package_count": len(packages), "unknown_count": sum(p["review_required"] for p in packages)}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")
print(f"License inventory: dependencies={len(packages)}; unknown/review={result['unknown_count']}; output={args.output}")
