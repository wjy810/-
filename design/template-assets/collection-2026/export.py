"""Export reviewed generation candidates; retain alpha and record actual output dimensions."""
import json
from pathlib import Path
from PIL import Image, ImageEnhance, ImageStat

ROOT = Path(__file__).resolve().parent
OUT = ROOT.parents[2] / "frontend/src/resume-render/assets/art"
CHOSEN = {
    "botanical-sprig": ("botanical-sprig-v1.png", "webp", 800),
    "paper-cotton": ("paper-cotton-v3.png", "jpg", 1240),
    "woven-strip": ("woven-strip-v1.png", "jpg", 900),
    "paper-cut": ("paper-cut-v2.png", "webp", 850),
    "shoreline-waves": ("shoreline-waves-v2.png", "webp", 1100),
    "porcelain-branch": ("porcelain-branch-v2.png", "webp", 800),
}

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    records = []
    for name, (source, kind, width) in CHOSEN.items():
        image = Image.open(ROOT / "originals" / source).convert("RGBA")
        original_size = image.size
        alpha = image.getchannel("A")
        transparent = sum(alpha.histogram()[:10]) / (image.width * image.height)
        if kind == "webp":
            if transparent < .45:
                raise ValueError(f"{source}: insufficient genuine transparency")
            box = alpha.point(lambda a: 255 if a > 8 else 0).getbbox()
            if not box:
                raise ValueError(f"{source}: empty ornament")
            image = image.crop(box)
        else:
            image = image.convert("RGB")
            if name == "paper-cotton":
                # Retain the generated fibre texture while making print brightness uniform and faint.
                image = ImageEnhance.Contrast(image).enhance(.22)
                mean = ImageStat.Stat(image).mean
                bands = [band.point(lambda v, m=m, t=t: max(0, min(255, round(v - m + t))))
                         for band, m, t in zip(image.split(), mean, (252, 250, 246))]
                image = Image.merge("RGB", bands)
        image.thumbnail((width, width * 2), Image.Resampling.LANCZOS)
        target = OUT / f"{name}.{kind}"
        image.save(target, "JPEG" if kind == "jpg" else "WEBP", quality=86,
                   **({"optimize": True} if kind == "jpg" else {"method": 6, "alpha_quality": 95}))
        records.append({"asset": name, "source": source, "originalSize": original_size,
                        "exportSize": image.size, "originalTransparentFraction": round(transparent, 4),
                        "bytes": target.stat().st_size})
        print(target.name, image.size, target.stat().st_size)
    (ROOT / "asset-quality.json").write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")

if __name__ == "__main__":
    main()
