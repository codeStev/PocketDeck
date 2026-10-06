"""German card names: normalisation of TCGdex names and derivation from Pokémon species names."""

import re
from collections import Counter

REGIONAL_PREFIXES = {
    "Alolan ": "Alola-",
    "Galarian ": "Galar-",
    "Hisuian ": "Hisui-",
    "Paldean ": "Paldea-",
}
MEGA_PREFIX = "Mega "
EX_SUFFIX = " ex"


def normalize_tcgdex_name(name: str) -> str:
    """TCGdex writes 'Glurak-ex'; the game (and the English data) write 'Glurak ex'."""
    return re.sub(r"-ex$", EX_SUFFIX, name.strip())


def tcgdex_id_to_master_id(tcgdex_id: str) -> str:
    """'A1-036' -> 'a1-036', 'P-A-001' -> 'pa-001'."""
    set_id, _, number = tcgdex_id.rpartition("-")
    return f"{set_id.replace('-', '').lower()}-{number}"


class SpeciesNames:
    """English -> German Pokémon species names, with the card-name decorations the game adds."""

    def __init__(self, species: dict):
        self._de_by_en = {
            _key(names["en"]): names["de"] for names in species.values() if "en" in names and "de" in names
        }

    def derive(self, english_card_name: str):
        """Returns the German card name, or None when the species cannot be identified."""
        name, suffix = english_card_name, ""
        if name.endswith(EX_SUFFIX):
            name, suffix = name[: -len(EX_SUFFIX)], EX_SUFFIX
        prefix = ""
        for english, german in REGIONAL_PREFIXES.items():
            if name.startswith(english):
                name, prefix = name[len(english) :], german
        if name.startswith(MEGA_PREFIX):
            name, prefix = name[len(MEGA_PREFIX) :], "Mega-"
        form = ""
        match = re.fullmatch(r"(.+) ([XY])", name)
        if prefix == "Mega-" and match:
            name, form = match.group(1), f" {match.group(2)}"
        german = self._de_by_en.get(_key(name))
        return f"{prefix}{german}{form}{suffix}" if german else None


def _key(name: str) -> str:
    # The gender symbols are what tells Nidoran♀ and Nidoran♂ apart, so they must survive the clean-up.
    spelled = name.casefold().replace("♀", "f").replace("♂", "m")
    return re.sub(r"[^a-z0-9]", "", spelled)


def match_ex_suffix(english: str, german: str) -> str:
    """The upstream German data sometimes drops the ' ex' that the card name carries."""
    if english.endswith(EX_SUFFIX) and not german.endswith(EX_SUFFIX):
        return german + EX_SUFFIX
    return german


class NameBook:
    """Resolves the German name of a card: exact TCGdex entry, then learned from TCGdex, then species."""

    def __init__(self, tcgdex_names_by_id: dict, english_by_id: dict, species: SpeciesNames):
        self._by_id = tcgdex_names_by_id
        self._species = species
        self._learned = self._learn(tcgdex_names_by_id, english_by_id)

    @staticmethod
    def _learn(german_by_id: dict, english_by_id: dict) -> dict:
        votes: dict = {}
        for card_id, german in german_by_id.items():
            english = english_by_id.get(card_id)
            if english:
                votes.setdefault(english, Counter())[german] += 1
        return {english: counter.most_common(1)[0][0] for english, counter in votes.items()}

    def resolve(self, card_id: str, english: str, is_pokemon: bool):
        """Returns (german_name, source) where source is tcgdex | learned | species | fallback."""
        if card_id in self._by_id:
            return match_ex_suffix(english, self._by_id[card_id]), "tcgdex"
        if english in self._learned:
            return match_ex_suffix(english, self._learned[english]), "learned"
        derived = self._species.derive(english) if is_pokemon else None
        if derived:
            return derived, "species"
        return english, "fallback"


def canonical_spellings(names) -> dict:
    """Maps every spelling to the most common spelling among those that differ only by spaces/punctuation.

    The upstream data contains typos such as 'GalarianZigzagoon' or 'Teal MaskOgerpon'; the properly spaced
    spelling is always also present, so the typo is repaired without any hand-written exception list.
    """
    counts = Counter(names)
    groups: dict = {}
    for name in counts:
        groups.setdefault(_key(name), []).append(name)
    canonical = {}
    for members in groups.values():
        best = max(members, key=lambda n: (counts[n], n.count(" "), n))
        for member in members:
            canonical[member] = best
    return canonical
