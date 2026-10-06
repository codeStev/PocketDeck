"""Downloads upstream datasets into a local cache so builds are repeatable and work offline."""

import json
import urllib.request
from pathlib import Path

V5_URL = "https://raw.githubusercontent.com/chase-manning/pokemon-tcg-pocket-cards/main/data/v5/cards.json"
FLIBUSTIER_URL = "https://raw.githubusercontent.com/flibustier/pokemon-tcg-pocket-database/main/dist/cards.json"
SPECIES_NAMES_URL = (
    "https://raw.githubusercontent.com/PokeAPI/pokeapi/master/data/v2/csv/pokemon_species_names.csv"
)
TCGDEX = "https://api.tcgdex.net/v2"
GERMAN_LANGUAGE_ID = "6"
ENGLISH_LANGUAGE_ID = "9"


class Cache:
    def __init__(self, directory: Path, refresh: bool = False):
        self.directory = directory
        self.refresh = refresh
        directory.mkdir(parents=True, exist_ok=True)

    def text(self, name: str, url: str) -> str:
        path = self.directory / name
        if self.refresh or not path.exists():
            request = urllib.request.Request(url, headers={"User-Agent": "pocketdeck-master-dataset"})
            with urllib.request.urlopen(request, timeout=60) as response:
                path.write_bytes(response.read())
        return path.read_text(encoding="utf-8")

    def json(self, name: str, url: str):
        return json.loads(self.text(name, url))


def load_v5(cache: Cache) -> list:
    return cache.json("v5_cards.json", V5_URL)


def load_flibustier(cache: Cache) -> list:
    return cache.json("flibustier_cards.json", FLIBUSTIER_URL)


def load_species_names(cache: Cache) -> dict:
    """Returns {species_id: {"en": name, "de": name}} from the PokeAPI CSV export."""
    lines = cache.text("species_names.csv", SPECIES_NAMES_URL).splitlines()
    header = lines[0].split(",")
    id_col, lang_col, name_col = (header.index(c) for c in ("pokemon_species_id", "local_language_id", "name"))
    names: dict = {}
    for row in _csv_rows(lines[1:]):
        language = {GERMAN_LANGUAGE_ID: "de", ENGLISH_LANGUAGE_ID: "en"}.get(row[lang_col])
        if language:
            names.setdefault(row[id_col], {})[language] = row[name_col]
    return names


def _csv_rows(lines):
    import csv

    return csv.reader(lines)


def load_tcgdex_german(cache: Cache) -> dict:
    """Returns {"sets": {set_id: name}, "cards": {tcgdex_card_id: {"name":..., "image":...}}}."""
    series = cache.json("tcgdex_de_series.json", f"{TCGDEX}/de/series/tcgp")
    sets, cards = {}, {}
    for entry in series["sets"]:
        detail = cache.json(f"tcgdex_de_set_{entry['id']}.json", f"{TCGDEX}/de/sets/{entry['id']}")
        sets[entry["id"]] = detail["name"]
        for card in detail["cards"]:
            cards[card["id"]] = {"name": card["name"], "image": card.get("image")}
    return {"sets": sets, "cards": cards}
