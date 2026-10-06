import unittest

import numpy as np

from cardindex import fingerprint as fp


class AreaWeightsTests(unittest.TestCase):
    def test_rows_sum_to_one(self):
        for source, target in ((10, 4), (273, 48), (159, 28), (28, 28)):
            self.assertTrue(np.allclose(fp.area_weights(source, target).sum(axis=1), 1.0), (source, target))

    def test_integer_ratio_averages_exact_blocks(self):
        weights = fp.area_weights(4, 2)

        self.assertEqual(weights.tolist(), [[0.5, 0.5, 0, 0], [0, 0, 0.5, 0.5]])

    def test_fractional_ratio_splits_boundary_pixels(self):
        weights = fp.area_weights(3, 2)  # each target pixel covers 1.5 source pixels

        self.assertTrue(np.allclose(weights, [[2 / 3, 1 / 3, 0], [0, 1 / 3, 2 / 3]]))


class GrayTests(unittest.TestCase):
    def test_integer_luma_formula(self):
        rgb = np.array([[[255, 255, 255], [0, 0, 0], [255, 0, 0], [0, 255, 0], [0, 0, 255]]], dtype=np.uint8)

        self.assertEqual(fp.to_gray(rgb).tolist(), [[255, 0, 76, 150, 29]])


class FingerprintTests(unittest.TestCase):
    def card(self, value=128, size=(512, 367)):
        return np.full((size[0], size[1], 3), value, dtype=np.uint8)

    def test_thumbnail_has_the_configured_size(self):
        thumbnail = fp.fingerprint(self.card())

        self.assertEqual(thumbnail.shape, (fp.OUT_HEIGHT, fp.OUT_WIDTH))
        self.assertEqual(thumbnail.dtype, np.uint8)

    def test_flat_images_keep_their_brightness(self):
        self.assertTrue((fp.fingerprint(self.card(200)) == 200).all())

    def test_only_the_illustration_region_matters(self):
        card = self.card(100)
        altered = card.copy()
        altered[:int(0.10 * 512)] = 255  # card name bar above the illustration
        altered[int(0.55 * 512):] = 0  # text boxes below it

        self.assertTrue((fp.fingerprint(card) == fp.fingerprint(altered)).all())

    def test_scale_does_not_change_the_fingerprint_much(self):
        def smooth(height, width):
            y, x = np.mgrid[0:height, 0:width]
            wave = 127 + 60 * np.sin(x / width * 9) + 50 * np.cos(y / height * 7)
            return np.stack([wave, wave * 0.8, wave * 0.6], axis=-1).astype(np.uint8)

        difference = np.abs(fp.fingerprint(smooth(512, 367)).astype(int) - fp.fingerprint(smooth(1024, 734)).astype(int))

        self.assertLessEqual(difference.max(), 3)

    def test_region_is_exactly_representable_as_float32(self):
        for value in fp.REGION:
            self.assertEqual(float(np.float32(value)), value)


if __name__ == "__main__":
    unittest.main()
