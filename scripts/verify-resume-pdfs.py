"""Inspect the synthetic twelve-template exports and render every page for review."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

import pdfplumber
from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--renders", required=True, type=Path)
    parser.add_argument("--pdftoppm", default=shutil.which("pdftoppm"))
    args = parser.parse_args()
    if not args.pdftoppm:
        parser.error("pdftoppm is required for visual verification")
    pdfs = sorted(args.input.glob("rlt-*.pdf"))
    if len(pdfs) != 12:
        raise ValueError(f"Expected exactly twelve template PDFs, found {len(pdfs)}")
    args.output.mkdir(parents=True, exist_ok=True)
    args.renders.mkdir(parents=True, exist_ok=True)
    required = [
        "\u6797\u77e5\u8fdc", "visual-qa@example.com", "13800001111",
        "Platform Engineer", "Computer Science", "JobProof Resume Platform",
        "Backend Owner", "OpenTelemetry",
        "Improved observability and tested rollback paths",
        "Verified immutable snapshot hashes",
        "\u5b8c\u6210\u6570\u636e\u5e93", "\u8f6f\u4ef6\u6d4b\u8bd5\u4e0e\u6280\u672f\u5199\u4f5c",
    ]
    compact = lambda value: "".join(value.split())
    results = []
    thumbnails = []
    for source in pdfs:
        result = {"template": source.stem, "sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "pages": []}
        with pdfplumber.open(source) as pdf:
            # This fixture is deliberately short enough to fit one A4 page in every template.
            if len(pdf.pages) != 1:
                raise AssertionError(f"Unexpected pagination in the short fixture: {source.name}")
            text = "\n".join(page.extract_text() or "" for page in pdf.pages)
            missing = [part for part in required if compact(part) not in compact(text)]
            if missing:
                raise AssertionError(f"Missing fixture text in {source.name}: {missing!r}")
            for index, page in enumerate(pdf.pages, start=1):
                if abs(page.width - 595.276) > 1 or abs(page.height - 841.89) > 1:
                    raise AssertionError(f"Non-A4 page in {source.name}")
                chars = [char for char in page.chars if char["text"].strip()]
                if len(chars) < 100:
                    raise AssertionError(f"Blank or incomplete page in {source.name}")
                outside = [char for char in chars if char["x0"] < -1 or char["x1"] > page.width + 1
                           or char["top"] < -1 or char["bottom"] > page.height + 1]
                if outside:
                    raise AssertionError(f"Text outside page bounds in {source.name}")
                result["pages"].append({"page": index, "width": page.width, "height": page.height,
                                        "characters": len(chars), "outOfBounds": len(outside)})
        prefix = args.renders / source.stem
        subprocess.run([args.pdftoppm, "-png", "-r", "100", str(source), str(prefix)], check=True,
                       capture_output=True, timeout=60)
        rendered = sorted(args.renders.glob(f"{source.stem}-*.png"))
        if len(rendered) != len(result["pages"]):
            raise AssertionError(f"Not every page was rendered for {source.name}")
        for path in rendered:
            with Image.open(path) as image:
                thumb = image.convert("RGB")
                thumb.thumbnail((350, 496))
                tile = Image.new("RGB", (374, 532), "#e9edf2")
                tile.paste(thumb, ((374 - thumb.width) // 2, 25))
                ImageDraw.Draw(tile).text((8, 7), path.stem, fill="#20262d")
                thumbnails.append(tile)
        destination = args.output / source.name
        shutil.copy2(source, destination)
        result["file"] = str(destination.resolve())
        result["missingText"] = []
        results.append(result)
    for start in range(0, len(thumbnails), 6):
        batch = thumbnails[start:start + 6]
        sheet = Image.new("RGB", (374 * 3, 532 * 2), "white")
        for index, tile in enumerate(batch):
            sheet.paste(tile, ((index % 3) * 374, (index // 3) * 532))
        sheet.save(args.renders / f"contact-sheet-{start // 6 + 1}.png")
    summary = {"templates": len(results), "pages": sum(len(item["pages"]) for item in results),
               "results": results, "visualReview": "Required; automated geometry does not prove no overlap."}
    (args.renders / "pdf-verification.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps({"templates": summary["templates"], "pages": summary["pages"], "checks": "passed"}))


if __name__ == "__main__":
    main()
