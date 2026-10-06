#!/usr/bin/env python3
"""Trim, resize and convert generated PNGs to WebP for the frontend.

Usage: python3 design/illustrations/export.py
Reads freshly generated design/illustrations/src/*.png when present, otherwise the
committed masters/*.webp (1024px), and writes frontend/src/shared/assets/illustrations/*.webp.
Variant files `-vN` are ignored unless promoted by renaming.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"
MASTERS = ROOT / "masters"
OUT = ROOT.parents[1] / "frontend" / "src" / "shared" / "assets" / "illustrations"

# Longest side in pixels (≈ 2x the largest display size).
SIZES = {
    "hero-resume": 640,
    "welcome-desk": 760,
    "ai-orb": 160,
}
DEFAULT_SIZE = 420
QUALITY = 84


def trim(image: Image.Image, margin_ratio: float = 0.04) -> Image.Image:
    alpha = image.getchannel("A").point(lambda a: 255 if a > 8 else 0)
    box = alpha.getbbox()
    if not box:
        return image
    left, top, right, bottom = box
    margin = int(max(right - left, bottom - top) * margin_ratio)
    return image.crop(
        (
            max(0, left - margin),
            max(0, top - margin),
            min(image.width, right + margin),
            min(image.height, bottom + margin),
        )
    )


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    total = 0
    sources = {path.stem: path for path in MASTERS.glob("*.webp")}
    sources.update({path.stem: path for path in SRC.glob("*.png")})
    for stem in sorted(sources):
        source = sources[stem]
        suffix = stem.rsplit("-", 1)[-1]
        if suffix.startswith("v") and suffix[1:].isdigit():
            continue
        image = trim(Image.open(source).convert("RGBA"))
        limit = SIZES.get(source.stem, DEFAULT_SIZE)
        image.thumbnail((limit, limit), Image.LANCZOS)
        target = OUT / f"{source.stem}.webp"
        image.save(target, "WEBP", quality=QUALITY, method=6)
        size = target.stat().st_size
        total += size
        print(f"{target.name:28s} {image.width:4d}x{image.height:<4d} {size / 1024:6.1f} KB")
    print(f"total {total / 1024:.1f} KB")


if __name__ == "__main__":
    main()
