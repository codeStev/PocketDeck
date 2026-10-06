"""Text format of digit_templates.txt, one template per line:

    <digit> <origin: game|synthetic> <width> <height> <hex of width*height grayscale bytes, row major>
"""

import numpy as np


def write_templates(templates: list) -> str:
    lines = []
    for digit, origin, bitmap in templates:
        height, width = bitmap.shape
        lines.append(f"{digit} {origin} {width} {height} {bitmap.astype(np.uint8).tobytes().hex()}")
    return "\n".join(lines) + "\n"


def read_templates(text: str) -> list:
    templates = []
    for line in text.strip().splitlines():
        digit, origin, width, height, payload = line.split(" ")
        data = np.frombuffer(bytes.fromhex(payload), dtype=np.uint8)
        if data.size != int(width) * int(height):
            raise ValueError(f"template {digit}: expected {int(width) * int(height)} bytes, got {data.size}")
        templates.append((int(digit), origin, data.reshape(int(height), int(width))))
    return templates
