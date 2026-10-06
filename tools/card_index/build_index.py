#!/usr/bin/env python3
"""Builds card_index.bin from the master dataset. Usage: build_index.py [--master PATH] [--out PATH] [--cache DIR]"""

import argparse
import json
import sys
from pathlib import Path

from cardindex import fingerprint as fp
from cardindex.images import decode_rgb, download_all
from cardindex.index_format import IndexFile, write_index

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
    entries = [
        (card["id"], fp.fingerprint(decode_rgb(paths[card["id"]].read_bytes())).tobytes()) for card in cards
    ]
    index = IndexFile(fp.OUT_WIDTH, fp.OUT_HEIGHT, fp.REGION, entries)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(write_index(index))
    print(f"wrote {len(entries)} fingerprints ({args.out.stat().st_size / 1e6:.1f} MB) to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
