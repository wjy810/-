#!/usr/bin/env python3
"""Export the chosen template assets into the renderer (frontend/src/resume-render/assets/art).

Opaque paper textures → JPEG (Chromium embeds JPEG data into the PDF as-is, keeping files small).
Transparent art → lossy WebP with alpha (small bundle), downscaled: it prints faint, so ~900px across is
plenty. PDF size follows pixel count (Chromium re-encodes non-JPEG images), not the source format.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"
OUT = ROOT.parents[1] / "frontend/src/resume-render/assets/art"

CHOSEN = {
    # name in renderer : (source file, kind, max width)
    "paper-warm": ("paper-warm-v1.png", "jpeg", 1240),
    "paper-cool": ("paper-cool-v1.png", "jpeg", 1240),
    "paper-linen": ("paper-linen-v2.png", "jpeg", 1240),
    "ink-mountains": ("ink-mountains-v1.png", "webp", 1100),
    "ink-bamboo": ("ink-bamboo-v2.png", "webp", 700),
    "ink-plum": ("ink-plum-v1.png", "webp", 760),
    "ink-wash-band": ("ink-wash-band-v1.png", "webp", 900),
    "wash-indigo": ("wash-indigo-v2.png", "webp", 1000),
    "wash-sage": ("wash-sage-v1.png", "webp", 1000),
    "wash-terracotta": ("wash-terracotta-v2.png", "webp", 1000),
    "topo-lines": ("topo-lines-v1.png", "webp", 900),
    "botanical-corner": ("botanical-corner-v2.png", "webp", 760),
    "aurora-soft": ("aurora-soft-v1.png", "webp", 820),
}


def crop_to_content(image: Image.Image) -> Image.Image:
    """Trim fully transparent margins so the art can be placed precisely."""
    box = image.getchannel("A").point(lambda a: 255 if a > 6 else 0).getbbox()
    return image.crop(box) if box else image


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (source, kind, width) in CHOSEN.items():
        image = Image.open(SRC / source)
        if kind == "jpeg":
            image = image.convert("RGB")
            image.thumbnail((width, width * 2), Image.LANCZOS)
            target = OUT / f"{name}.jpg"
            image.save(target, "JPEG", quality=82, optimize=True, progressive=True)
        else:
            image = crop_to_content(image.convert("RGBA"))
            image.thumbnail((width, width * 2), Image.LANCZOS)
            target = OUT / f"{name}.webp"
            image.save(target, "WEBP", quality=86, method=6, alpha_quality=90)
        print(f"{target.name}: {image.size[0]}x{image.size[1]} {target.stat().st_size // 1024}KB")


if __name__ == "__main__":
    main()
