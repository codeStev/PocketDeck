#!/usr/bin/env python3
"""Builds master_cards.json. Usage: build_master.py [--out PATH] [--cache DIR] [--refresh]"""

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

from masterlib import sources
from masterlib.merge import build_master

HERE = Path(__file__).resolve().parent
DEFAULT_OUT = HERE.parent.parent / "app" / "src" / "main" / "assets" / "master_cards.json"


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--cache", type=Path, default=HERE / ".cache")
    parser.add_argument("--refresh", action="store_true", help="re-download all upstream datasets")
    args = parser.parse_args(argv)

    cache = sources.Cache(args.cache, args.refresh)
    master = build_master(
        sources.load_v5(cache),
        sources.load_flibustier(cache),
        sources.load_species_names(cache),
        sources.load_tcgdex_german(cache),
    )
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(master, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    sources_count = Counter(card["germanNameSource"] for card in master["cards"])
    print(f"wrote {len(master['cards'])} cards to {args.out}")
    print("german name sources:", dict(sources_count))
    return 0


if __name__ == "__main__":
    sys.exit(main())
