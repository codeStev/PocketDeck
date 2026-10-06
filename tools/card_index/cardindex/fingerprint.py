"""Illustration fingerprints. The Kotlin recognizer (adapter/recognition) implements the same algorithm;
the index header carries the parameters so both sides cannot drift apart silently."""

import numpy as np

# Multiples of 1/16 so they are exactly representable as float32 in the index header.
REGION = (0.0625, 0.125, 0.9375, 0.5)  # x0, y0, x1, y1 as fractions of the card
OUT_WIDTH = 48
OUT_HEIGHT = 28


def area_weights(source_size: int, target_size: int) -> np.ndarray:
    """Matrix (target x source) that averages the source pixels covered by each target pixel, exactly."""
    scale = source_size / target_size
    weights = np.zeros((target_size, source_size), dtype=np.float64)
    for target in range(target_size):
        start, end = target * scale, (target + 1) * scale
        for source in range(int(start), min(int(np.ceil(end)), source_size)):
            overlap = min(end, source + 1) - max(start, source)
            weights[target, source] = max(overlap, 0.0) / scale
    return weights


def to_gray(rgb: np.ndarray) -> np.ndarray:
    """Integer luma, identical on both platforms: (299 R + 587 G + 114 B + 500) / 1000."""
    pixels = rgb.astype(np.int64)
    return (299 * pixels[..., 0] + 587 * pixels[..., 1] + 114 * pixels[..., 2] + 500) // 1000


def crop_region(rgb: np.ndarray, region=REGION) -> np.ndarray:
    height, width = rgb.shape[:2]
    x0, y0, x1, y1 = int(region[0] * width), int(region[1] * height), int(region[2] * width), int(region[3] * height)
    return rgb[y0:y1, x0:x1]


def fingerprint(rgb: np.ndarray, region=REGION, out_width=OUT_WIDTH, out_height=OUT_HEIGHT) -> np.ndarray:
    """Returns an (out_height x out_width) uint8 grayscale thumbnail of the card's illustration."""
    gray = to_gray(crop_region(rgb, region)).astype(np.float64)
    resized = area_weights(gray.shape[0], out_height) @ gray @ area_weights(gray.shape[1], out_width).T
    return np.clip(np.rint(resized), 0, 255).astype(np.uint8)
