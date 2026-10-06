#!/usr/bin/env python3
"""Builds card_index.bin from the master dataset. Usage: build_index.py [--master PATH] [--out PATH] [--cache DIR]"""

import argparse
import json
import sys
from pathlib import Path

from cardindex import fingerprint as fp
from cardindex.images import decode_rgb, download_all
from cardindex.index_format import IndexFile, write_index
from cardindex.twins import twin_groups

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MASTER = ROOT / "app" / "src" / "main" / "assets" / "master_cards.json"
DEFAULT_OUT = ROOT / "app" / "src" / "main" / "assets" / "card_index.bin"


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--master", type=Path, default=DEFAULT_MASTER)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--cache", type=Path, default=Path(__file__).resolve().parent / ".cache")
    args = parser.parse_args(argv)

    cards = json.loads(args.master.read_text(encoding="utf-8"))["cards"]
    # English art is complete for every card; the illustration region does not depend on the print language.
    paths = download_all({c["id"]: c["artwork"]["en"] for c in cards}, args.cache)
    thumbnails = {c["id"]: fp.fingerprint(decode_rgb(paths[c["id"]].read_bytes())) for c in cards}
    ids = [c["id"] for c in cards]
    groups = twin_groups(ids, np.stack([thumbnails[i].ravel() for i in ids]), [c["gameplayKey"] for c in cards])
    # Reprints with identical artwork and gameplay cannot be told apart on screen: only the lowest id of each group
    # stays in the index, so a tile always resolves to the same card.
    entries = [(i, thumbnails[i].tobytes()) for i in ids if groups[i] == i]
    index = IndexFile(fp.OUT_WIDTH, fp.OUT_HEIGHT, fp.REGION, entries)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(write_index(index))
    print(f"{len(ids)} cards form {len(entries)} distinct artworks ({len(ids) - len(entries)} reprints merged)")
    print(f"wrote {len(entries)} fingerprints ({args.out.stat().st_size / 1e6:.1f} MB) to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
