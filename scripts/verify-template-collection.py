"""Inspect the new collection's actual PDFs and compare rasterized pages with browser previews."""
import argparse
import json
from pathlib import Path
import fitz
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]

def compact(value):
    return "".join(value.split()).lower()

def contact_sheet(images, target, columns=4):
    font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 17)
    tile_w, tile_h = 340, 510
    sheet = Image.new("RGB", (columns * tile_w, ((len(images) + columns - 1) // columns) * tile_h), "#ecece7")
    draw = ImageDraw.Draw(sheet)
    for index, (label, source) in enumerate(images):
        image = Image.open(source).convert("RGB")
        image.thumbnail((316, 460))
        x, y = index % columns * tile_w + 12, index // columns * tile_h + 35
        sheet.paste(image, (x, y))
        draw.text((x, y - 27), label, font=font, fill="#26362f")
    sheet.save(target)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=ROOT / ".local-run/template-collection-20261021")
    args = parser.parse_args()
    out = args.input
    manifests = json.loads((ROOT / "backend/src/main/resources/templates/builtin-manifests.json").read_text("utf-8"))["templates"][16:]
    samples = json.loads((ROOT / "backend/src/test/resources/templates/render-samples.json").read_text("utf-8"))
    records, overview, continuation = [], [], []
    for manifest in manifests:
        template_id = manifest["id"]
        sample = samples["english" if manifest["locale"] == "en" else "professional"]
        pdf = fitz.open(out / f"{template_id}.pdf")
        text = compact("\n".join(page.get_text(sort=False) for page in pdf))
        required = [sample["basics"][key] for key in ("name", "email", "phone") if sample["basics"].get(key)]
        for section, key in (("experiences", "company"), ("projects", "name"), ("education", "school"), ("certificates", "name")):
            required.extend(item[key] for item in sample.get(section, []) if item.get(key))
        missing = [value for value in required if compact(value) not in text]
        assert not missing, (template_id, missing)
        assert len(pdf) <= manifest["maxPages"], (template_id, "too many pages")
        pages = []
        for index, page in enumerate(pdf):
            assert abs(page.rect.width - 595.276) < 1 and abs(page.rect.height - 841.89) < 1
            fonts = page.get_fonts(full=True)
            assert fonts and all(pdf.extract_font(font[0])[3] for font in fonts), (template_id, "unembedded font")
            spans = [span for block in page.get_text("dict")["blocks"] if "lines" in block for line in block["lines"] for span in line["spans"]]
            outside = [span["text"] for span in spans if span["bbox"][0] < -1 or span["bbox"][2] > page.rect.width + 1 or span["bbox"][1] < -1 or span["bbox"][3] > page.rect.height + 1]
            assert not outside, (template_id, outside)
            assert any(span["text"].strip() for span in spans)
            raster = page.get_pixmap(matrix=fitz.Matrix(96 / 72, 96 / 72), alpha=False)
            image = Image.frombytes("RGB", (raster.width, raster.height), raster.samples)
            target = out / f"{template_id}-pdf-page-{index + 1}.png"
            image.save(target)
            preview = Image.open(out / f"{template_id}-page-{index + 1}.png").convert("RGB").resize(image.size, Image.Resampling.LANCZOS)
            difference = np.abs(np.asarray(image).astype(float) - np.asarray(preview).astype(float))
            mean = float(difference.mean())
            assert mean < 6, (template_id, index, "preview/PDF mismatch", mean)
            pages.append({"page": index + 1, "fontsEmbedded": len(fonts), "outsideText": outside,
                          "previewPdfMeanAbsoluteDifference": round(mean, 3), "links": len(page.get_links())})
            (overview if index == 0 else continuation).append((manifest["name"] + (" / p" + str(index + 1) if index else ""), target))
        assert sum(page["links"] for page in pages) > 0, (template_id, "links missing")
        records.append({"template": template_id, "pages": pages, "missingText": missing, "bytes": (out / f"{template_id}.pdf").stat().st_size})
        print(template_id, len(pdf), "pages; text, fonts, links, geometry and visual comparison PASS")
    contact_sheet(overview, out / "pdf-overview.png")
    if continuation:
        contact_sheet(continuation, out / "continuations.png")
    (out / "pdf-verification.json").write_text(json.dumps({"templates": len(records), "results": records}, indent=2) + "\n", encoding="utf-8")

if __name__ == "__main__":
    main()
