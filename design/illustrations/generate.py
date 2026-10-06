#!/usr/bin/env python3
"""Generate JobProof AI illustrations with an OpenAI-compatible image API.

Usage:
  JP_IMAGE_API_BASE=https://.../v1 JP_IMAGE_API_KEY=sk-... \
    python3 design/illustrations/generate.py [name ...] [--force] [--variants N]

Every asset shares STYLE so the set stays visually consistent. Raw PNGs are
written to design/illustrations/src/<name>[-vN].png; pick the best variant and
run export.py to produce the WebP files the frontend ships.
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

STYLE = (
    "Soft 3D clay illustration, rounded chunky friendly shapes, smooth matte clay material, "
    "soft diffused studio lighting with gentle contact shadow, pastel palette of ink indigo (#5E59E8), "
    "soft lavender (#CBCAFB), warm apricot orange (#FA8C55) and creamy off-white paper (#F6F1EA), "
    "subtle mint green accents only when needed, isolated subject centered with generous empty padding, "
    "transparent background, no text, no letters, no numbers, no logos, no watermark, clean and premium."
)

ASSETS: dict[str, dict[str, str]] = {
    # --- Landing hero sub-elements (composited in CSS) ---
    "hero-resume": {"size": "1024x1536", "subject": "a tall portrait resume paper sheet standing upright and slightly tilted, with a round avatar badge at the top left, several rounded indigo and lavender text-line bars, three bullet dots, and a small apricot bookmark ribbon hanging from the top right corner"},
    "hero-check": {"size": "1024x1024", "subject": "a round shield-shaped badge with a bold thick checkmark, indigo shield body with an apricot rim, slightly tilted, glossy highlight"},
    "hero-magnifier": {"size": "1024x1024", "subject": "a magnifying glass with a chunky apricot handle and a glossy slightly lavender-tinted lens, tilted 30 degrees"},
    "hero-chat": {"size": "1024x1024", "subject": "a rounded speech bubble in soft lavender with three indigo dots inside, and a small apricot four-pointed sparkle star at its top right"},
    "hero-target": {"size": "1024x1024", "subject": "a small archery target with rings in indigo, cream and apricot, with an indigo arrow hitting the bullseye, slightly turned to the side"},
    "hero-sparkles": {"size": "1024x1024", "subject": "a loose cluster of three four-pointed sparkle stars of different sizes, one apricot, one lavender, one cream, floating"},
    "hero-plane": {"size": "1024x1024", "subject": "a folded paper airplane in creamy white with indigo folded edges, flying upward to the right"},
    "hero-cap": {"size": "1024x1024", "subject": "a graduation mortarboard cap in ink indigo with an apricot tassel, slightly tilted"},
    "hero-pencil": {"size": "1024x1024", "subject": "a chunky short pencil with an apricot body, cream wood tip and an indigo eraser, lying diagonally"},
    # --- Empty / error states ---
    "empty-resume": {"size": "1024x1024", "subject": "a blank creamy sheet of paper lying slightly tilted with a chunky apricot pencil resting across it and a tiny lavender sparkle above"},
    "empty-match": {"size": "1024x1024", "subject": "two small document cards overlapping, one indigo-accented and one apricot-accented, with a magnifying glass hovering over where they overlap"},
    "empty-canvas": {"size": "1024x1024", "subject": "a folded paper map in cream with a dotted indigo path leading to an apricot location pin, and a small compass beside it"},
    "empty-interview": {"size": "1024x1024", "subject": "a retro studio microphone in indigo with an apricot grille band on a small stand, and a little lavender speech bubble floating beside it"},
    "empty-notification": {"size": "1024x1024", "subject": "a calm notification bell in apricot resting on a soft lavender cushion, with a tiny cream crescent moon floating above it"},
    "empty-folder": {"size": "1024x1024", "subject": "an open indigo folder with a few creamy paper sheets peeking out and a small apricot star sticker on the front"},
    "empty-search": {"size": "1024x1024", "subject": "a magnifying glass with an apricot handle looking at an empty dotted lavender circle on the ground"},
    "empty-templates": {"size": "1024x1024", "subject": "a fan of three resume template cards spread out, each with a different header color: indigo, apricot and lavender, with simple line bars"},
    "error-plane": {"size": "1024x1024", "subject": "a gently crumpled paper airplane that has landed nose-down, with a small apricot adhesive bandage on its wing, a few tiny lavender dust puffs"},
    # --- Onboarding identities ---
    "identity-student": {"size": "1024x1024", "subject": "a cute school backpack in ink indigo with apricot straps and zipper pulls, a small cream notebook peeking out"},
    "identity-graduate": {"size": "1024x1024", "subject": "a rolled diploma scroll tied with an apricot ribbon resting against an indigo graduation cap"},
    "identity-professional": {"size": "1024x1024", "subject": "a modern rounded briefcase in ink indigo with an apricot handle and cream clasp"},
    # --- Assistant / auth / dashboard ---
    "ai-orb": {"size": "1024x1024", "subject": "a smooth glossy sphere with a soft indigo to violet to apricot gradient, with a small cream four-pointed sparkle star on its upper right, like a friendly AI assistant avatar"},
    "auth-key": {"size": "1024x1024", "subject": "a chunky key with a plain round apricot bow (no face, no eyes, no decorations) and an indigo blade, floating next to a small open lavender padlock"},
    "welcome-desk": {"size": "1536x1024", "subject": "a cozy tiny desk scene: an open laptop whose screen shows a resume layout with indigo bars, a cream coffee mug, a small potted plant with rounded leaves, a stack of two notebooks in apricot and lavender, and a few sparkles floating above"},
    "dash-match": {"size": "1024x1024", "subject": "an archery target in indigo and cream rings with an apricot arrow in the center"},
    "dash-planning": {"size": "1024x1024", "subject": "a small rounded signpost with three arrow boards in indigo, apricot and lavender pointing different directions on a little grassy mint base"},
    "dash-interview": {"size": "1024x1024", "subject": "two overlapping speech bubbles, a larger indigo one and a smaller apricot one, with a tiny sparkle"},
    "success-trophy": {"size": "1024x1024", "subject": "a small rounded trophy cup in apricot with an indigo base and a cream star emblem, a few lavender sparkles around it"},
    "success-confetti": {"size": "1024x1024", "subject": "a party popper cone in indigo bursting with rounded confetti pieces and ribbons in apricot, lavender and cream"},
    "ink-pen": {"size": "1024x1024", "subject": "an elegant chunky fountain pen with an ink indigo body and an apricot gold nib, resting diagonally on a small cream paper card that has a drawn indigo checkmark stroke"},
    "rocket": {"size": "1024x1024", "subject": "a small rounded rocket in cream with an indigo nose cone and fins, an apricot porthole rim, and a soft lavender cloud puff trail, launching upward to the right"},
    "dash-library": {"size": "1024x1024", "subject": "a neat stack of three closed books and a folder in indigo, apricot and lavender with a small bookmark"},
}


def call_api(name: str, spec: dict[str, str], variant: int) -> Path:
    payload = {
        "model": MODEL,
        "prompt": f"{spec['subject']}. {STYLE}",
        "size": spec["size"],
        "background": "transparent",
        "output_format": "png",
        "quality": "high",
        "n": 1,
    }
    request = urllib.request.Request(
        f"{API_BASE}/images/generations",
        data=json.dumps(payload).encode(),
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
    )
    last_error: Exception | None = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=420) as response:
                body = json.loads(response.read())
            image = base64.b64decode(body["data"][0]["b64_json"])
            suffix = f"-v{variant}" if variant else ""
            out = SRC / f"{name}{suffix}.png"
            out.write_bytes(image)
            return out
        except Exception as error:  # noqa: BLE001 - retry any transport/API failure
            last_error = error
            time.sleep(4 * (attempt + 1))
    raise RuntimeError(f"{name}: {last_error}")


def main(argv: list[str]) -> int:
    if not API_BASE or not API_KEY:
        print("JP_IMAGE_API_BASE and JP_IMAGE_API_KEY are required", file=sys.stderr)
        return 2
    force = "--force" in argv
    variants = 1
    if "--variants" in argv:
        variants = int(argv[argv.index("--variants") + 1])
    names = [a for a in argv if not a.startswith("--") and not a.isdigit()] or list(ASSETS)
    SRC.mkdir(parents=True, exist_ok=True)
    jobs = []
    for name in names:
        for v in range(variants):
            suffix = f"-v{v}" if v else ""
            if not force and (SRC / f"{name}{suffix}.png").exists():
                continue
            jobs.append((name, v))
    print(f"generating {len(jobs)} images with {MODEL}")
    failures = 0
    with ThreadPoolExecutor(max_workers=int(os.environ.get("JP_IMAGE_CONCURRENCY", "6"))) as pool:
        futures = {pool.submit(call_api, n, ASSETS[n], v): (n, v) for n, v in jobs}
        for future in as_completed(futures):
            try:
                print("ok", future.result().name, flush=True)
            except Exception as error:  # noqa: BLE001
                failures += 1
                print("FAILED", error, flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
