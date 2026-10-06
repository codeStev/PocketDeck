"""Merges the upstream datasets into one master record per printed card."""

import hashlib
from datetime import datetime, timezone

from .names import NameBook, SpeciesNames, canonical_spellings, normalize_tcgdex_name, tcgdex_id_to_master_id

SCHEMA_VERSION = 1
KEY_DIGEST_HEX_LENGTH = 12
STAGES = {"Basic": "basic", "Stage 1": "stage1", "Stage 2": "stage2"}
TRAINER_KINDS = {"Supporter": "supporter", "Item": "item", "Tool": "tool", "Stadium": "stadium"}
POKEMON_TYPE = "Pokémon"
TRAINER_TYPE = "Trainer"


class MasterDataError(ValueError):
    """An upstream record has a shape the master dataset does not understand."""


def gameplay_key(card: dict) -> str:
    """Readable name plus a digest of everything that makes same-named cards play differently."""
    if card["type"] == POKEMON_TYPE:
        attacks = ";".join(
            f"{a['cost']}|{a['name']}|{a['damage']}|{a['effect']}"
            for _, a in sorted(card["attacks"].items())
            if a.get("name")
        )
        ability = card["ability"]
        ability_text = f"{ability['name']}|{ability['effect']}" if ability and ability.get("exists") else ""
        parts = [card["subtype"], card["stage"], card["health"], card["retreat"], card["weakness"], attacks, ability_text]
        fingerprint = "/".join(str(part) for part in parts)
    else:
        fingerprint = card.get("card_text") or ""
    digest = hashlib.sha256(fingerprint.encode("utf-8")).hexdigest()[:KEY_DIGEST_HEX_LENGTH]
    return f"{card['name']}#{digest}"


def _attacks(card: dict) -> list:
    return [
        {"cost": a["cost"] or "", "name": a["name"], "damage": a["damage"], "effect": a["effect"]}
        for _, a in sorted(card["attacks"].items())
        if a.get("name")
    ]


def _pokemon_fields(card: dict, names: NameBook, evolves_from_de) -> dict:
    stage = STAGES.get(card["stage"])
    if stage is None:
        raise MasterDataError(f"{card['id']}: unknown stage {card['stage']!r}")
    ability = card["ability"] if card.get("ability") and card["ability"].get("exists") else None
    return {
        "energyType": card["subtype"].lower(),
        "stage": stage,
        "evolvesFrom": (
            {"en": card["evolves_from"], "de": evolves_from_de(card["evolves_from"])} if card["evolves_from"] else None
        ),
        "hp": card["health"],
        "retreatCost": card["retreat"],
        "weakness": card["weakness"].lower() if card.get("weakness") else None,
        "ability": {"name": ability["name"], "effect": ability["effect"]} if ability else None,
        "attacks": _attacks(card),
        "isEx": bool(card.get("ex")),
        "isMega": bool(card.get("mega")),
    }


def _trainer_fields(card: dict) -> dict:
    kind = TRAINER_KINDS.get(card["subtype"])
    if kind is None:
        raise MasterDataError(f"{card['id']}: unknown trainer kind {card['subtype']!r}")
    return {"trainerKind": kind, "text": card.get("card_text")}


def repair_names(v5: list) -> tuple:
    """Returns (cards with canonical English names, number of repaired name fields)."""
    spellings = canonical_spellings([c["name"] for c in v5] + [c["evolves_from"] for c in v5 if c.get("evolves_from")])
    repaired, count = [], 0
    for card in v5:
        fixed = dict(card, name=spellings[card["name"]])
        if card.get("evolves_from"):
            fixed["evolves_from"] = spellings[card["evolves_from"]]
        count += (fixed["name"] != card["name"]) + (fixed.get("evolves_from") != card.get("evolves_from"))
        repaired.append(fixed)
    return repaired, count


def build_master(v5: list, flibustier: list, species: dict, tcgdex: dict, generated_at: str = None) -> dict:
    """Pure function over already-loaded sources, so it can be tested without any network."""
    v5, repaired_names = repair_names(v5)
    extras = {(c["set"].lower(), int(c["number"])): c for c in flibustier}
    german_names = {
        tcgdex_id_to_master_id(card_id): normalize_tcgdex_name(entry["name"])
        for card_id, entry in tcgdex["cards"].items()
    }
    german_images = {tcgdex_id_to_master_id(i): e.get("image") for i, e in tcgdex["cards"].items() if e.get("image")}
    english_by_id = {card["id"]: card["name"] for card in v5}
    book = NameBook(german_names, english_by_id, SpeciesNames(species))

    def evolves_from_de(english: str) -> str:
        return book.resolve("", english, is_pokemon=True)[0]

    cards = [_card_record(card, extras, book, german_images, evolves_from_de) for card in v5]
    cards.sort(key=lambda record: record["id"])
    ids = [record["id"] for record in cards]
    if len(set(ids)) != len(ids):
        raise MasterDataError("duplicate card ids in the merged dataset")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": generated_at or datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "repairedNameFields": repaired_names,
        "sets": _sets(v5, tcgdex),
        "cards": cards,
    }


def _sets(v5: list, tcgdex: dict) -> list:
    sets = {}
    for card in v5:
        sets.setdefault(card["set_code"], {"id": card["set_code"], "en": card["set_name"], "releaseDate": card["release_date"]})
    german_set_names = {set_id.replace("-", "").lower(): name for set_id, name in tcgdex["sets"].items()}
    for set_id, record in sets.items():
        record["de"] = german_set_names.get(set_id)
    # Promo sets have no release date; they sort first.
    return sorted(sets.values(), key=lambda s: (s["releaseDate"] or "", s["id"]))


def _card_record(card, extras, book, german_images, evolves_from_de) -> dict:
    if card["type"] not in (POKEMON_TYPE, TRAINER_TYPE):
        raise MasterDataError(f"{card['id']}: unknown card type {card['type']!r}")
    is_pokemon = card["type"] == POKEMON_TYPE
    german, source = book.resolve(card["id"], card["name"], is_pokemon)
    number = int(card["id"].rsplit("-", 1)[1])
    extra = extras.get((card["set_code"], number), {})
    record = {
        "id": card["id"],
        "set": card["set_code"],
        "number": number,
        "kind": "pokemon" if is_pokemon else "trainer",
        "names": {"en": card["name"], "de": german},
        "germanNameSource": source,
        "gameplayKey": gameplay_key(card),
        "rarity": card["rarity"],
        "rarityCode": extra.get("rarity"),
        "packs": extra.get("packs") or ([card["pack"]] if card.get("pack") else []),
        "artwork": {"en": card["image"], "de": _german_artwork(german_images.get(card["id"]))},
    }
    record.update(_pokemon_fields(card, book, evolves_from_de) if is_pokemon else _trainer_fields(card))
    return record


def _german_artwork(base_url):
    return f"{base_url}/high.webp" if base_url else None
