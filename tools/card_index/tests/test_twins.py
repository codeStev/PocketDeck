import unittest

import numpy as np

from cardindex.twins import twin_groups


def thumbnail(seed: int, noise: float = 0.0, size: int = 64) -> np.ndarray:
    rng = np.random.default_rng(seed)
    base = rng.integers(0, 255, size).astype(np.float32)
    return np.clip(base + np.random.default_rng(seed + 1000).normal(0, noise, size), 0, 255)


class TwinGroupTests(unittest.TestCase):
    def test_identical_artwork_with_the_same_gameplay_is_one_group(self):
        ids = ["b4b-001", "a1-001", "pa-001"]
        thumbs = np.stack([thumbnail(1), thumbnail(1, noise=2), thumbnail(1, noise=3)])

        groups = twin_groups(ids, thumbs, ["Pikachu#x"] * 3)

        self.assertEqual(set(groups.values()), {"a1-001"})

    def test_the_lowest_id_represents_the_group(self):
        groups = twin_groups(["b2-010", "a3-010"], np.stack([thumbnail(5), thumbnail(5)]), ["K"] * 2)

        self.assertEqual(groups, {"b2-010": "a3-010", "a3-010": "a3-010"})

    def test_different_artwork_stays_apart(self):
        groups = twin_groups(["a1-001", "a1-002"], np.stack([thumbnail(1), thumbnail(2)]), ["A", "A"])

        self.assertEqual(groups, {"a1-001": "a1-001", "a1-002": "a1-002"})

    def test_same_artwork_with_different_gameplay_is_never_merged(self):
        groups = twin_groups(["a1-001", "a9-001"], np.stack([thumbnail(3), thumbnail(3)]), ["Pikachu#old", "Pikachu#new"])

        self.assertEqual(groups, {"a1-001": "a1-001", "a9-001": "a9-001"})

    def test_a_card_without_twins_represents_itself(self):
        self.assertEqual(twin_groups(["a1-001"], np.stack([thumbnail(1)]), ["A"]), {"a1-001": "a1-001"})

    def test_a_chain_of_twins_ends_in_one_group(self):
        ids = ["c-3", "b-2", "a-1"]
        thumbs = np.stack([thumbnail(7, 1), thumbnail(7, 2), thumbnail(7, 3)])

        self.assertEqual(set(twin_groups(ids, thumbs, ["K"] * 3).values()), {"a-1"})


if __name__ == "__main__":
    unittest.main()
