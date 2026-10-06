#!/usr/bin/env python3
"""Generate decorative assets for the resume templates (textures, ink and watercolour art).

Usage:
  JP_IMAGE_API_BASE=https://.../v1 JP_IMAGE_API_KEY=sk-... \
    python3 design/template-assets/generate.py [name ...] [--variants N] [--force]

Raw PNGs land in design/template-assets/src/<name>-v<N>.png. Pick variants by eye (they print on
paper, so they must stay faint, clean and free of text), then run export.py to produce the files the
renderer ships. Geometric patterns are hand-written SVG instead (crisper in print).
"""
from __future__ import annotations

import base64
import json
import os
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"
API_BASE = os.environ.get("JP_IMAGE_API_BASE", "").rstrip("/")
API_KEY = os.environ.get("JP_IMAGE_API_KEY", "")
MODEL = os.environ.get("JP_IMAGE_MODEL", "gpt-image-2.5-flare")

PRINT = ("Designed to be printed faintly behind text on an A4 resume: extremely clean, high resolution, "
         "no text, no letters, no numbers, no logos, no watermark, no signature, no frame, no border.")

ASSETS: dict[str, dict[str, str]] = {
    # Full-bleed paper textures (opaque, tiled or stretched behind the page).
    "paper-warm": {"size": "1024x1536", "bg": "opaque", "prompt": "Flat scan of fine warm ivory cotton writing paper, extremely subtle even fibre texture, uniform soft lighting, no shadows, no vignette, no stains, seamless feel, color close to #FBF8F2."},
    "paper-cool": {"size": "1024x1536", "bg": "opaque", "prompt": "Flat scan of smooth cool white premium printing paper, extremely subtle fine grain, uniform lighting, no shadows, no vignette, no stains, color close to #F7F8FA."},
    "paper-linen": {"size": "1024x1536", "bg": "opaque", "prompt": "Flat scan of off-white linen-embossed stationery paper, very fine crosshatch weave barely visible, uniform lighting, no shadows, no vignette, color close to #F8F6F1."},
    # Chinese ink-wash pieces (transparent, used at low opacity).
    "ink-mountains": {"size": "1536x1024", "bg": "transparent", "prompt": "Traditional Chinese ink wash painting of distant layered mountains fading into mist, painted only along the bottom third, wide horizontal panorama, light to mid grey ink only, soft wet edges, the top two thirds completely empty and transparent."},
    "ink-bamboo": {"size": "1024x1536", "bg": "transparent", "prompt": "Elegant Chinese ink painting of a few bamboo stalks and leaves entering from the right edge, sparse, grey and soft black ink, lots of empty transparent space on the left."},
    "ink-plum": {"size": "1536x1024", "bg": "transparent", "prompt": "Chinese ink painting of a single plum blossom branch reaching in from the top right corner, grey ink branch with a few tiny muted red blossoms, extremely sparse and airy, rest of the image empty and transparent."},
    "ink-wash-band": {"size": "1536x1024", "bg": "transparent", "prompt": "A single wide horizontal brush stroke of diluted grey ink wash across the middle, soft dry-brush edges, airy and minimal, everything else empty and transparent."},
    # Watercolour washes (transparent header bands).
    "wash-indigo": {"size": "1536x1024", "bg": "transparent", "prompt": "Soft watercolor wash in muted indigo blue, a horizontal band across the top third that fades to nothing downward, delicate paper bleed edges, gentle granulation, everything below empty and transparent."},
    "wash-sage": {"size": "1536x1024", "bg": "transparent", "prompt": "Soft watercolor wash in muted sage green, a horizontal band across the top third that fades to nothing downward, delicate bleed edges, gentle granulation, everything below empty and transparent."},
    "wash-terracotta": {"size": "1536x1024", "bg": "transparent", "prompt": "Soft watercolor wash in muted terracotta and sand, a horizontal band across the top third fading downward, delicate bleed edges, gentle granulation, everything below empty and transparent."},
    # Line art (transparent).
    "topo-lines": {"size": "1536x1024", "bg": "transparent", "prompt": "Topographic contour map lines, thin uniform hairlines in light slate grey, flowing organic elevation rings clustered toward the right side, clean vector look, no labels, transparent background."},
    "botanical-corner": {"size": "1024x1024", "bg": "transparent", "prompt": "Fine single-weight line drawing of eucalyptus and fern sprigs arranged as an elegant corner ornament in the top right, thin hairlines in muted sage grey, botanical illustration, transparent background."},
    "aurora-soft": {"size": "1536x1024", "bg": "transparent", "prompt": "Very soft blurred aurora gradient glow in pale lavender, periwinkle and peach, a diffuse cloud of color concentrated near the top right corner fading smoothly to fully transparent, no hard edges, no objects."},
}


def call_api(name: str, spec: dict[str, str], variant: int) -> Path:
    payload = {
        "model": MODEL,
        "prompt": f"{spec['prompt']} {PRINT}",
        "size": spec["size"],
        "n": 1,
        "quality": "high",
        "output_format": "png",
        "background": spec["bg"],
    }
    request = urllib.request.Request(
        f"{API_BASE}/images/generations",
        data=json.dumps(payload).encode(),
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
    )
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                body = json.load(response)
            item = body["data"][0]
            data = base64.b64decode(item["b64_json"]) if item.get("b64_json") else urllib.request.urlopen(item["url"], timeout=120).read()
            target = SRC / f"{name}-v{variant}.png"
            target.write_bytes(data)
            return target
        except Exception as error:  # noqa: BLE001 - retry any transport or API failure
            if attempt == 2:
                raise RuntimeError(f"{name} v{variant}: {error}") from error
            time.sleep(4 * (attempt + 1))
    raise AssertionError("unreachable")


def main(argv: list[str]) -> int:
    if not API_BASE or not API_KEY:
        print("Set JP_IMAGE_API_BASE and JP_IMAGE_API_KEY", file=sys.stderr)
        return 2
    force = "--force" in argv
    variants = int(argv[argv.index("--variants") + 1]) if "--variants" in argv else 2
    names = [a for i, a in enumerate(argv) if not a.startswith("--") and (i == 0 or argv[i - 1] != "--variants")] or list(ASSETS)
    SRC.mkdir(parents=True, exist_ok=True)
    jobs = [(n, v) for n in names for v in range(1, variants + 1) if force or not (SRC / f"{n}-v{v}.png").exists()]
    print(f"generating {len(jobs)} images")
    with ThreadPoolExecutor(max_workers=4) as pool:
        futures = {pool.submit(call_api, n, ASSETS[n], v): (n, v) for n, v in jobs}
        for future in as_completed(futures):
            try:
                print("ok", future.result().name, flush=True)
            except Exception as error:  # noqa: BLE001
                print("FAILED", error, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
