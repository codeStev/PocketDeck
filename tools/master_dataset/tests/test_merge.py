import copy
import unittest

from masterlib.merge import MasterDataError, build_master, gameplay_key

EMPTY_ATTACK = {"cost": None, "name": None, "damage": None, "effect": None}


def pokemon(card_id="a1-001", name="Bulbasaur", **overrides):
    card = {
        "id": card_id, "name": name, "set_code": card_id.split("-")[0], "set_name": "Genetic Apex",
        "pack": "Mewtwo", "release_date": "2024-10-30", "type": "Pokémon", "subtype": "Grass",
        "stage": "Basic", "evolves_from": None, "rarity": "◊", "ex": False, "mega": False,
        "health": 70, "retreat": 1, "weakness": "Fire", "ability": {"exists": False, "name": None, "effect": None},
        "card_text": None, "attacks": {"1": {"cost": "GC", "name": "Vine Whip", "damage": 40, "effect": None}, "2": EMPTY_ATTACK},
        "image": f"https://img.test/{card_id}.webp",
    }
    card.update(overrides)
    return card


def trainer(card_id="a1-219", name="Erika", **overrides):
    card = pokemon(card_id, name, type="Trainer", subtype="Supporter", stage=None, health=None, retreat=None,
                   weakness=None, attacks={"1": EMPTY_ATTACK, "2": EMPTY_ATTACK}, card_text="Heal 50.")
    card.update(overrides)
    return card


SPECIES = {"1": {"en": "Bulbasaur", "de": "Bisasam"}, "2": {"en": "Ivysaur", "de": "Bisaknosp"}}
TCGDEX = {"sets": {"A1": "Unschlagbare Gene"}, "cards": {"A1-001": {"name": "Bisasam", "image": "https://x.test/de/A1/001"}}}
FLIBUSTIER = [
    {"set": "A1", "number": 1, "rarity": "C", "packs": ["Mewtwo", "Pikachu"], "image": "cPK_10_000010_00_FUSHIGIDANE_C.webp"},
    {"set": "PROMO-A", "number": 7, "rarity": "P", "packs": [], "image": "cTR_90_000040_00_HAKASENOKENKYU_C.webp"},
]


def build(v5):
    return build_master(v5, FLIBUSTIER, SPECIES, TCGDEX, generated_at="2026-10-06T00:00:00+00:00")


class MergeTests(unittest.TestCase):
    def test_merges_all_sources_into_one_record(self):
        record = build([pokemon()])["cards"][0]

        self.assertEqual(record["names"], {"en": "Bulbasaur", "de": "Bisasam"})
        self.assertEqual(record["germanNameSource"], "tcgdex")
        self.assertEqual(record["artwork"], {"en": "https://img.test/a1-001.webp", "de": "https://x.test/de/A1/001/high.webp"})
        self.assertEqual(record["rarityCode"], "C")
        self.assertEqual(record["packs"], ["Mewtwo", "Pikachu"])
        self.assertEqual((record["kind"], record["energyType"], record["stage"], record["hp"]), ("pokemon", "grass", "basic", 70))
        self.assertEqual(record["attacks"], [{"cost": "GC", "name": "Vine Whip", "damage": 40, "effect": None}])

    def test_records_the_games_own_id_of_each_card(self):
        # The game's deck codes name cards by the number in the asset file name, not by set and number.
        pokemon_record = build([pokemon()])["cards"][0]
        promo = build([trainer("pa-007", "Professor's Research", set_code="pa")])["cards"][0]

        self.assertEqual(pokemon_record["gameEntityId"], 10)
        self.assertEqual(promo["gameEntityId"], 40)

    def test_a_card_the_games_database_does_not_know_has_no_game_id(self):
        record = build([pokemon("a1-002", "Ivysaur", stage="Stage 1", evolves_from="Bulbasaur", health=90)])["cards"][0]

        self.assertIsNone(record["gameEntityId"])

    def test_evolution_records_name_their_predecessor_in_both_languages(self):
        ivysaur = pokemon("a1-002", "Ivysaur", stage="Stage 1", evolves_from="Bulbasaur", health=90)

        record = build([ivysaur])["cards"][0]

        self.assertEqual(record["evolvesFrom"], {"en": "Bulbasaur", "de": "Bisasam"})
        self.assertEqual(record["names"]["de"], "Bisaknosp")
        self.assertEqual(record["germanNameSource"], "species")
        self.assertIsNone(record["artwork"]["de"])

    def test_trainers_carry_kind_and_text(self):
        record = build([trainer()])["cards"][0]

        self.assertEqual((record["kind"], record["trainerKind"], record["text"]), ("trainer", "supporter", "Heal 50."))
        self.assertEqual(record["germanNameSource"], "fallback")

    def test_cards_are_sorted_and_set_names_are_localised(self):
        master = build([pokemon("a1-002", "Ivysaur", stage="Stage 1", evolves_from="Bulbasaur"), pokemon()])

        self.assertEqual([c["id"] for c in master["cards"]], ["a1-001", "a1-002"])
        self.assertEqual(master["sets"], [{"id": "a1", "en": "Genetic Apex", "releaseDate": "2024-10-30", "de": "Unschlagbare Gene"}])

    def test_typos_in_evolution_references_are_repaired(self):
        base = pokemon("b4-001", "Galarian Zigzagoon")
        evolved = pokemon("b4-096", "Galarian Linoone", stage="Stage 1", evolves_from="GalarianZigzagoon", health=90)

        master = build([base, evolved])

        self.assertEqual(master["cards"][1]["evolvesFrom"]["en"], "Galarian Zigzagoon")
        self.assertEqual(master["repairedNameFields"], 1)

    def test_sets_without_a_release_date_sort_first(self):
        promo = pokemon("pa-001", "Bulbasaur", set_name="Promo-A", release_date=None)

        master = build([pokemon(), promo])

        self.assertEqual([s["id"] for s in master["sets"]], ["pa", "a1"])
        self.assertIsNone(master["sets"][0]["releaseDate"])

    def test_duplicate_ids_are_rejected(self):
        with self.assertRaises(MasterDataError):
            build([pokemon(), pokemon()])

    def test_unknown_stage_is_rejected_and_names_the_card(self):
        with self.assertRaisesRegex(MasterDataError, "a1-001"):
            build([pokemon(stage="Mega")])

    def test_unknown_trainer_kind_is_rejected(self):
        with self.assertRaisesRegex(MasterDataError, "a1-219"):
            build([trainer(subtype="Gadget")])

    def test_unknown_card_type_is_rejected(self):
        with self.assertRaises(MasterDataError):
            build([pokemon(type="Energy")])

    def test_build_is_deterministic(self):
        self.assertEqual(build([pokemon()]), build([pokemon()]))


class GameplayKeyTests(unittest.TestCase):
    def test_reprints_share_a_key_but_changed_attacks_do_not(self):
        original = pokemon("a1-094", "Pikachu")
        reprint = pokemon("a4b-999", "Pikachu", rarity="☆☆", image="https://img.test/other.webp")
        reworked = copy.deepcopy(original)
        reworked["attacks"]["1"]["damage"] = 50

        self.assertEqual(gameplay_key(original), gameplay_key(reprint))
        self.assertNotEqual(gameplay_key(original), gameplay_key(reworked))

    def test_key_starts_with_the_card_name(self):
        self.assertTrue(gameplay_key(pokemon()).startswith("Bulbasaur#"))


if __name__ == "__main__":
    unittest.main()
