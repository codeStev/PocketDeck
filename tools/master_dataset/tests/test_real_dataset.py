"""Quality checks on the real built dataset. Skipped until `build_master.py` has produced it."""

import json
import unittest
from pathlib import Path

DATASET = Path(__file__).resolve().parents[3] / "app" / "src" / "main" / "assets" / "master_cards.json"

# German names as displayed by the game itself (read off a real "Meine Karten" screenshot).
OBSERVED_IN_GAME = {
    "a2b-108": "Glurak ex",
    "b3a-100": "Klibbe",
    "b3b-098": "Kangama",
    "b4-214": "Sedimantur",
    "b4-215": "Legios",
    "b4a-103": "Schwalbini",
    "a1-281": "Pikachu ex",
    "b4-203": "Mega-Rayquaza ex",
    "a1-256": "Turtok ex",
}
EXPECTED_CARD_COUNT = 4317


@unittest.skipUnless(DATASET.exists(), "master_cards.json has not been built")
class RealDatasetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.master = json.loads(DATASET.read_text(encoding="utf-8"))
        cls.cards = {card["id"]: card for card in cls.master["cards"]}

    def test_contains_every_printed_card_once(self):
        self.assertEqual(len(self.master["cards"]), EXPECTED_CARD_COUNT)
        self.assertEqual(len(self.cards), EXPECTED_CARD_COUNT)

    def test_german_names_match_what_the_game_displays(self):
        for card_id, expected in OBSERVED_IN_GAME.items():
            self.assertEqual(self.cards[card_id]["names"]["de"], expected, card_id)

    def test_every_card_has_names_artwork_and_a_gameplay_key(self):
        for card in self.cards.values():
            self.assertTrue(card["names"]["en"] and card["names"]["de"], card["id"])
            self.assertTrue(card["artwork"]["en"], card["id"])
            self.assertIn("#", card["gameplayKey"], card["id"])

    def test_pokemon_have_battle_stats_and_trainers_have_a_kind(self):
        for card in self.cards.values():
            if card["kind"] == "pokemon":
                self.assertGreater(card["hp"], 0, card["id"])
                self.assertIn(card["stage"], ("basic", "stage1", "stage2"), card["id"])
                self.assertEqual(card["stage"] == "basic", card["evolvesFrom"] is None, card["id"])
            else:
                self.assertIn(card["trainerKind"], ("supporter", "item", "tool", "stadium"), card["id"])

    def test_ex_cards_keep_their_suffix_in_german(self):
        for card in self.cards.values():
            if card["names"]["en"].endswith(" ex"):
                self.assertTrue(card["names"]["de"].endswith(" ex"), card["id"])

    def test_gender_variants_are_not_confused(self):
        names = {card["names"]["de"] for card in self.cards.values() if card["names"]["en"].startswith("Nidoran")}
        self.assertTrue({"Nidoran♀", "Nidoran♂"} <= names, names)

    def test_most_german_names_come_from_real_german_data(self):
        fallback = sum(card["germanNameSource"] == "fallback" for card in self.cards.values())
        self.assertLess(fallback / len(self.cards), 0.10)

    def test_evolution_references_resolve_to_known_cards(self):
        english_names = {card["names"]["en"] for card in self.cards.values()}
        for card in self.cards.values():
            if card["kind"] == "pokemon" and card["evolvesFrom"]:
                reference = card["evolvesFrom"]["en"]
                self.assertTrue(reference in english_names, f"{card['id']} evolves from unknown card {reference!r}")


if __name__ == "__main__":
    unittest.main()
