import unittest

import numpy as np

from digittemplates.glyphs import digit_ink, split_glyphs

TAB_COLOR = (76, 92, 116)


def tile_with_badge(digits=(), height=442, width=318):
    """A card tile with the game's slate count tab; each entry of [digits] is a (x, w) white bar standing in for a glyph."""
    tile = np.full((height, width, 3), (200, 160, 90), dtype=np.uint8)
    top = int(0.90 * height)
    tile[top:, : int(0.46 * width)] = TAB_COLOR
    for x, glyph_width in digits:
        tile[top + 8 : top + 34, x : x + glyph_width] = 255
    return tile


class DigitInkTests(unittest.TestCase):
    def test_no_tab_means_no_badge(self):
        plain = np.full((442, 318, 3), (200, 160, 90), dtype=np.uint8)

        self.assertIsNone(digit_ink(plain))

    def test_a_faded_tab_is_not_a_badge(self):
        tile = tile_with_badge()
        tile[int(0.90 * 442) :, : int(0.46 * 318)] = (120, 130, 150)  # mid-fade: neither card nor slate

        self.assertIsNone(digit_ink(tile))

    def test_a_lighter_tab_near_the_navigation_bar_is_still_a_badge(self):
        tile = tile_with_badge([(60, 10)])
        tile[int(0.90 * 442) :, : int(0.46 * 318)] = (85, 108, 136)
        tile[int(0.90 * 442) + 8 : int(0.90 * 442) + 34, 60:70] = 255

        glyphs = split_glyphs(digit_ink(tile))

        self.assertEqual(len(glyphs), 1)

    def test_look_alike_pixels_above_the_tab_do_not_move_its_edges(self):
        tile = tile_with_badge([(60, 10)])
        tile[int(0.86 * 442), 100:108] = TAB_COLOR  # a few artwork pixels with the tab's colour, above the tab
        tile[int(0.87 * 442) : int(0.89 * 442), :] = 255  # bright card content between them and the tab

        glyphs = split_glyphs(digit_ink(tile))

        self.assertEqual([g.shape for g in glyphs], [(26, 10)])

    def test_a_visible_tab_yields_an_ink_patch(self):
        self.assertIsNotNone(digit_ink(tile_with_badge([(60, 10)])))


class SplitGlyphTests(unittest.TestCase):
    def glyphs(self, digits):
        return split_glyphs(digit_ink(tile_with_badge(digits)))

    def test_one_digit_gives_one_glyph_cropped_to_its_ink(self):
        glyphs = self.glyphs([(60, 10)])

        self.assertEqual(len(glyphs), 1)
        self.assertEqual(glyphs[0].shape, (26, 10))

    def test_two_digits_are_separated_by_their_gap(self):
        glyphs = self.glyphs([(40, 14), (62, 14)])

        self.assertEqual([g.shape[1] for g in glyphs], [14, 14])

    def test_an_empty_tab_gives_no_glyphs(self):
        self.assertEqual(self.glyphs([]), [])


if __name__ == "__main__":
    unittest.main()
