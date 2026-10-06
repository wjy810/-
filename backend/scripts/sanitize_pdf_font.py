#!/usr/bin/env python3
"""Remove CJK radical aliases that break PDF ToUnicode extraction."""

from argparse import ArgumentParser
from pathlib import Path

from fontTools.ttLib import TTFont


RADICAL_START = 0x2E80
RADICAL_END = 0x2FDF


def main() -> None:
    parser = ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    font = TTFont(args.input)
    removed = 0
    for table in font["cmap"].tables:
        if not table.isUnicode():
            continue
        aliases = [code_point for code_point in table.cmap if RADICAL_START <= code_point <= RADICAL_END]
        for code_point in aliases:
            del table.cmap[code_point]
        removed += len(aliases)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    font.save(args.output, reorderTables=True)
    print(f"Removed {removed} CJK radical cmap aliases from {args.input} -> {args.output}")


if __name__ == "__main__":
    main()
