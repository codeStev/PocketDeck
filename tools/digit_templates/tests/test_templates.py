import os
import unittest
from pathlib import Path

import numpy as np

from build_templates import build, real_glyphs
from digittemplates.matching import classify
from digittemplates.template_file import read_templates, write_templates

FONT = Path(os.environ.get("POCKETDECK_FONT", "fonts/Poppins-Bold.ttf"))
FRAMES = Path(os.environ.get("POCKETDECK_FRAMES", "/nonexistent"))
LABELS = Path(__file__).resolve().parents[1] / "labels.json"
UNCERTAIN_MARGIN = 0.05


class TemplateFileTests(unittest.TestCase):
    def test_round_trip(self):
        templates = [(1, "game", np.arange(6, dtype=np.uint8).reshape(3, 2)), (7, "synthetic", np.full((2, 2), 9, np.uint8))]

        restored = read_templates(write_templates(templates))

        self.assertEqual([(d, o) for d, o, _ in restored], [(1, "game"), (7, "synthetic")])
        self.assertTrue((restored[0][2] == templates[0][2]).all())

    def test_a_corrupt_payload_is_rejected(self):
        with self.assertRaises(ValueError):
            read_templates("3 game 2 2 00ff")


@unittest.skipUnless(FONT.exists(), "set POCKETDECK_FONT to a Poppins-Bold.ttf")
class SyntheticTests(unittest.TestCase):
    def test_every_digit_is_recognised_in_a_synthetic_only_set(self):
        templates = build({}, str(FONT))

        self.assertEqual(sorted(d for d, _, _ in templates), list(range(10)))
        for digit, _, bitmap in templates:
            self.assertEqual(classify(bitmap, templates)[0], digit)

    def test_real_glyphs_come_with_a_synthetic_fallback_for_every_digit(self):
        real = {1: [np.full((26, 9), 255, np.uint8)]}

        templates = build(real, str(FONT))

        self.assertEqual([(d, o) for d, o, _ in templates if o == "game"], [(1, "game")])
        self.assertEqual(sorted(d for d, o, _ in templates if o == "synthetic"), list(range(10)))


@unittest.skipUnless(FONT.exists() and FRAMES.exists(), "needs POCKETDECK_FONT and POCKETDECK_FRAMES (real settled frames)")
class LeaveOneFrameOutTests(unittest.TestCase):
    """Builds templates without one frame and reads that frame, whose digits may never have been seen for real."""

    def test_digits_of_an_unseen_frame_are_read_correctly(self):
        import json

        labels = json.loads(LABELS.read_text())
        from PIL import Image

        from digittemplates.glyphs import digit_ink, split_glyphs

        for held_out in labels:
            training = {k: v for k, v in labels.items() if k != held_out}
            templates = build(real_glyphs(FRAMES, training), str(FONT))
            frame = np.asarray(Image.open(FRAMES / held_out).convert("RGB"))
            entry = labels[held_out]
            for (x, y, w, h), text in zip(entry["tiles"], entry["digits"]):
                glyphs = split_glyphs(digit_ink(frame[y : y + h, x : x + w]))
                for expected, glyph in zip(text, glyphs):
                    digit, distance, margin = classify(glyph, templates)
                    # A stand-in glyph may be a poor match for the game's own digit; then the reading must at
                    # least be flagged as uncertain (small margin) instead of being confidently wrong.
                    self.assertTrue(
                        str(digit) == expected or margin < UNCERTAIN_MARGIN,
                        f"{held_out} tile {x},{y}: read {digit} instead of {expected}, margin {margin:.2f}",
                    )


if __name__ == "__main__":
    unittest.main()
