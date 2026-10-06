"""Reference implementation of glyph classification (used to validate the template set; Kotlin mirrors it)."""

import numpy as np
from PIL import Image

NORM_WIDTH, NORM_HEIGHT = 32, 40
SYNTHETIC_PENALTY = 0.0  # ranking is by shape alone; the reader lowers the confidence of synthetic matches instead


def normalize(glyph: np.ndarray) -> np.ndarray:
    image = Image.fromarray(glyph.astype(np.uint8)).resize((NORM_WIDTH, NORM_HEIGHT), Image.BILINEAR)
    values = (np.asarray(image).astype(float) - 90) / (255 - 90)
    return np.clip(values, 0, 1)


def classify(glyph: np.ndarray, templates: list):
    """Returns (digit, distance, margin) of the best template; lower distance is better."""
    candidate = normalize(glyph)
    best_per_digit = {}
    for digit, origin, bitmap in templates:
        distance = float(np.abs(candidate - normalize(bitmap)).mean())
        if origin == "synthetic":
            distance += SYNTHETIC_PENALTY
        best_per_digit[digit] = min(distance, best_per_digit.get(digit, 9.0))
    ranked = sorted(best_per_digit.items(), key=lambda item: item[1])
    return ranked[0][0], ranked[0][1], ranked[1][1] - ranked[0][1]
