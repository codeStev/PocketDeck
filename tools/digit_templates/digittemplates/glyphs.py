"""Finds and cuts out the digit glyphs of a count badge. The Kotlin reader (adapter/recognition) follows the same
geometry; the constants below are measured from real screenshots of the game's "My Cards" grid."""

import numpy as np

# Badge tab: a flat slate colour in the bottom-left corner of a tile, sized as fractions of the tile. The colour is
# (76, 92, 116) when the list rests and drifts lighter towards the navigation bar, so it is sampled per tile.
ANCHOR = (0.05, 0.965)  # (x, y) fractions of the tile where the tab always is
TAB_TOLERANCE = 8
SLATE_RED = (60, 110)
SLATE_BLUE_TINT = (28, 56)
SLATE_GREEN_TINT = (10, 26)
WINDOW_TOP = 0.84
WINDOW_RIGHT = 0.55
MIN_TAB_ROW_FRACTION = 0.25
MIN_TAB_HEIGHT_FRACTION = 0.06
TAB_MARGIN = (6, 2, 3)  # top, bottom, left; the top margin also hides the card plate edge that can peek above the tab
DIGIT_AREA_RIGHT = 0.40  # right limit of the digit area, relative to the tile width (the tab ends near 0.46)
GLYPH_THRESHOLD = 140  # minimum channel value that counts as digit ink


def tab_color(tile: np.ndarray):
    """The slate colour of this tile's tab, or None when the anchor spot is not slate (no badge visible)."""
    height, width = tile.shape[:2]
    red, green, blue = (int(v) for v in tile[int(ANCHOR[1] * height), int(ANCHOR[0] * width)])
    ok = (
        SLATE_RED[0] <= red <= SLATE_RED[1]
        and SLATE_BLUE_TINT[0] <= blue - red <= SLATE_BLUE_TINT[1]
        and SLATE_GREEN_TINT[0] <= green - red <= SLATE_GREEN_TINT[1]
    )
    return np.array([red, green, blue]) if ok else None


def longest_run(flags: np.ndarray):
    best, start = None, None
    for i, flag in enumerate(list(flags) + [False]):
        if flag and start is None:
            start = i
        elif not flag and start is not None:
            if best is None or i - start > best[1]:
                best = (start, i - start)
            start = None
    return best


def digit_ink(tile: np.ndarray):
    """Returns the grayscale (min channel) patch inside the badge tab, or None when no badge tab is visible."""
    color = tab_color(tile)
    if color is None:
        return None
    height, width = tile.shape[:2]
    window = tile[int(WINDOW_TOP * height) :, : int(WINDOW_RIGHT * width)]
    mask = (np.abs(window.astype(int) - color) <= TAB_TOLERANCE).all(axis=-1)
    run = longest_run(mask.sum(axis=1) >= MIN_TAB_ROW_FRACTION * width)
    if run is None or run[1] < MIN_TAB_HEIGHT_FRACTION * height:
        return None
    top, length = run
    bottom = top + length - 1
    left = int(np.argmax(mask[top : bottom + 1].sum(axis=0) >= length / 2))
    area_right = int(DIGIT_AREA_RIGHT * width)
    return window[top + TAB_MARGIN[0] : bottom - TAB_MARGIN[1], left + TAB_MARGIN[2] : area_right].min(axis=-1)


def split_glyphs(ink: np.ndarray) -> list:
    """Cuts the ink patch into one cropped grayscale bitmap per glyph, left to right."""
    columns = (ink > GLYPH_THRESHOLD).any(axis=0)
    glyphs, start = [], None
    for x, has_ink in enumerate(list(columns) + [False]):
        if has_ink and start is None:
            start = x
        elif not has_ink and start is not None:
            part = ink[:, start:x]
            rows = np.where((part > GLYPH_THRESHOLD).any(axis=1))[0]
            glyphs.append(part[rows.min() : rows.max() + 1])
            start = None
    return glyphs
