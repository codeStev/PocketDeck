"""Glyphs for digits that have not been seen in the real game yet, drawn with a similar geometric bold font."""

import numpy as np
from PIL import Image, ImageDraw, ImageFont

TARGET_HEIGHT = 26  # digit height in pixels on a 3-per-row screen


def render_digit(font_path: str, digit: int) -> np.ndarray:
    """Grayscale glyph (0-255), cropped to its ink and scaled to the real digit height."""
    font = ImageFont.truetype(font_path, 160)
    canvas = Image.new("L", (300, 300), 0)
    ImageDraw.Draw(canvas).text((30, 10), str(digit), font=font, fill=255)
    pixels = np.asarray(canvas)
    ys, xs = np.where(pixels > 128)
    glyph = Image.fromarray(pixels[ys.min() : ys.max() + 1, xs.min() : xs.max() + 1])
    width = max(1, round(glyph.width * TARGET_HEIGHT / glyph.height))
    return np.asarray(glyph.resize((width, TARGET_HEIGHT), Image.LANCZOS))
