"""Reprints share their artwork, and on screen they cannot be told apart. Cards that look alike AND play the same are
merged into one group so a tile always resolves to the same card; the lowest id of a group represents it."""

import numpy as np

SIMILARITY = 0.98  # reference thumbnails this alike are the same artwork (distinct cards stay below ~0.9)


def normalize(thumbnails: np.ndarray) -> np.ndarray:
    """Rows of mean-centred, unit-length vectors, so that a dot product is the correlation."""
    vectors = thumbnails.astype(np.float32)
    vectors -= vectors.mean(axis=1, keepdims=True)
    return vectors / (np.linalg.norm(vectors, axis=1, keepdims=True) + 1e-6)


def twin_groups(card_ids: list, thumbnails: np.ndarray, gameplay_keys: list, threshold: float = SIMILARITY) -> dict:
    """Maps every card id to the lowest id of its group. Two cards are twins only if their artwork matches AND their
    gameplay key is identical, so same-looking cards with different effects are never merged."""
    vectors = normalize(thumbnails)
    similarity = vectors @ vectors.T
    parent = list(range(len(card_ids)))

    def find(node: int) -> int:
        while parent[node] != node:
            parent[node] = parent[parent[node]]
            node = parent[node]
        return node

    rows, columns = np.where(np.triu(similarity >= threshold, 1))
    for a, b in zip(rows, columns):
        if gameplay_keys[a] == gameplay_keys[b]:
            root_a, root_b = find(int(a)), find(int(b))
            if root_a != root_b:
                parent[max(root_a, root_b)] = min(root_a, root_b)
    lowest: dict = {}
    for index, card_id in enumerate(card_ids):
        root = find(index)
        lowest[root] = min(lowest.get(root, card_id), card_id)
    return {card_id: lowest[find(index)] for index, card_id in enumerate(card_ids)}
