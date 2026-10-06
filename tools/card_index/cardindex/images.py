"""Downloads card images into a cache and decodes them."""

import io
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image


def download(url: str, target: Path) -> None:
    if target.exists() and target.stat().st_size > 0:
        return
    request = urllib.request.Request(url, headers={"User-Agent": "pocketdeck-card-index"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                target.write_bytes(response.read())
            return
        except OSError:
            if attempt == 2:
                raise


def download_all(urls_by_id: dict, cache: Path, workers: int = 16) -> dict:
    cache.mkdir(parents=True, exist_ok=True)
    paths = {card_id: cache / f"{card_id}.webp" for card_id in urls_by_id}
    with ThreadPoolExecutor(workers) as pool:
        list(pool.map(lambda item: download(urls_by_id[item[0]], item[1]), paths.items()))
    return paths


def decode_rgb(data: bytes) -> np.ndarray:
    """Decodes an image, compositing transparency (rounded card corners) onto white."""
    image = Image.open(io.BytesIO(data)).convert("RGBA")
    background = Image.new("RGBA", image.size, (255, 255, 255, 255))
    background.alpha_composite(image)
    return np.asarray(background.convert("RGB"))
