#!/usr/bin/env python3
"""Builds digit_templates.txt. Real glyphs come from labelled screenshots of settled "My Cards" frames, the digits
never seen so far are drawn with a similar font.

Usage: build_templates.py --frames DIR --labels labels.json --font Poppins-Bold.ttf [--out PATH]
labels.json: {"frames/f009.png": {"tiles": [[x, y, w, h], ...], "digits": ["1", "10", ...]}, ...}
(a label with several characters is a multi-digit count; each character matches one glyph, left to right)
"""

import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from digittemplates.glyphs import digit_ink, split_glyphs
from digittemplates.synthetic import render_digit
from digittemplates.template_file import write_templates

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUT = ROOT / "adapter" / "recognition" / "src" / "main" / "resources" / "digit_templates.txt"


def real_glyphs(frames_dir: Path, labels: dict) -> dict:
    """Returns {digit: [bitmap, ...]} from the labelled tiles."""
    found: dict = {}
    for name, entry in labels.items():
        frame = np.asarray(Image.open(frames_dir / name).convert("RGB"))
        for (x, y, w, h), text in zip(entry["tiles"], entry["digits"]):
            ink = digit_ink(frame[y : y + h, x : x + w])
            glyphs = split_glyphs(ink) if ink is not None else []
            if len(glyphs) != len(text):
                raise ValueError(f"{name}: tile at {x},{y} labelled {text!r} but yielded {len(glyphs)} glyph(s)")
            for character, glyph in zip(text, glyphs):
                found.setdefault(int(character), []).append(glyph)
    return found


def average_template(bitmaps: list) -> np.ndarray:
    """One representative glyph per digit: the pixel-wise median of the examples on a common canvas."""
    height = max(b.shape[0] for b in bitmaps)
    width = max(b.shape[1] for b in bitmaps)
    canvas = [np.asarray(Image.fromarray(b).resize((width, height), Image.BILINEAR)) for b in bitmaps]
    return np.median(np.stack(canvas), axis=0).astype(np.uint8)


def build(real: dict, font_path: str) -> list:
    """Real glyphs where the game's own digits have been seen, plus a drawn glyph for every digit as a fallback."""
    templates = [(digit, "game", average_template(real[digit])) for digit in sorted(real)]
    templates += [(digit, "synthetic", render_digit(font_path, digit)) for digit in range(10)]
    return templates


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frames", type=Path, required=True)
    parser.add_argument("--labels", type=Path, required=True)
    parser.add_argument("--font", required=True)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args(argv)
    real = real_glyphs(args.frames, json.loads(args.labels.read_text()))
    templates = build(real, args.font)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(write_templates(templates))
    print("real digits:", sorted(real), "| templates:", len(templates))
    return 0


if __name__ == "__main__":
    sys.exit(main())
