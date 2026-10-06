import unittest

from masterlib.names import NameBook, SpeciesNames, canonical_spellings, match_ex_suffix, normalize_tcgdex_name, tcgdex_id_to_master_id

SPECIES = {
    "1": {"en": "Bulbasaur", "de": "Bisasam"},
    "6": {"en": "Charizard", "de": "Glurak"},
    "26": {"en": "Raichu", "de": "Raichu"},
    "122": {"en": "Mr. Mime", "de": "Pantimos"},
    "384": {"en": "Rayquaza", "de": "Rayquaza"},
}


class NormalizeTests(unittest.TestCase):
    def test_tcgdex_ex_suffix_uses_a_space_like_the_game(self):
        self.assertEqual(normalize_tcgdex_name("Glurak-ex"), "Glurak ex")

    def test_other_hyphens_are_kept(self):
        self.assertEqual(normalize_tcgdex_name("Ho-Oh"), "Ho-Oh")

    def test_ids_are_mapped_to_master_ids(self):
        self.assertEqual(tcgdex_id_to_master_id("A1-036"), "a1-036")
        self.assertEqual(tcgdex_id_to_master_id("A1a-007"), "a1a-007")
        self.assertEqual(tcgdex_id_to_master_id("P-A-001"), "pa-001")


class SpeciesDerivationTests(unittest.TestCase):
    def setUp(self):
        self.species = SpeciesNames(SPECIES)

    def test_plain_species(self):
        self.assertEqual(self.species.derive("Bulbasaur"), "Bisasam")

    def test_ex_suffix_is_kept(self):
        self.assertEqual(self.species.derive("Charizard ex"), "Glurak ex")

    def test_mega_prefix_gets_a_hyphen(self):
        self.assertEqual(self.species.derive("Mega Rayquaza ex"), "Mega-Rayquaza ex")

    def test_mega_forms_keep_their_letter(self):
        self.assertEqual(self.species.derive("Mega Charizard X ex"), "Mega-Glurak X ex")

    def test_regional_forms(self):
        self.assertEqual(self.species.derive("Alolan Raichu"), "Alola-Raichu")

    def test_punctuation_is_ignored_when_matching(self):
        self.assertEqual(self.species.derive("Mr Mime"), "Pantimos")

    def test_gender_symbols_keep_the_nidorans_apart(self):
        species = SpeciesNames({"29": {"en": "Nidoran♀", "de": "Nidoran♀"}, "32": {"en": "Nidoran♂", "de": "Nidoran♂"}})

        self.assertEqual(species.derive("Nidoran♀"), "Nidoran♀")
        self.assertEqual(species.derive("Nidoran♂"), "Nidoran♂")

    def test_unknown_species_yields_none(self):
        self.assertIsNone(self.species.derive("Poké Ball"))


class CanonicalSpellingTests(unittest.TestCase):
    def test_missing_spaces_are_repaired_towards_the_common_spelling(self):
        spellings = canonical_spellings(["Galarian Zigzagoon", "GalarianZigzagoon", "Galarian Zigzagoon", "Pikachu"])

        self.assertEqual(spellings["GalarianZigzagoon"], "Galarian Zigzagoon")
        self.assertEqual(spellings["Pikachu"], "Pikachu")

    def test_a_lone_spelling_is_never_changed(self):
        self.assertEqual(canonical_spellings(["Mr. Mime"]), {"Mr. Mime": "Mr. Mime"})

    def test_ties_prefer_the_spelling_with_spaces(self):
        self.assertEqual(canonical_spellings(["Teal MaskOgerpon", "Teal Mask Ogerpon"])["Teal MaskOgerpon"], "Teal Mask Ogerpon")

    def test_gender_variants_stay_distinct(self):
        spellings = canonical_spellings(["Nidoran♀", "Nidoran♂"])

        self.assertEqual(spellings["Nidoran♀"], "Nidoran♀")
        self.assertEqual(spellings["Nidoran♂"], "Nidoran♂")


class ExSuffixTests(unittest.TestCase):
    def test_a_missing_suffix_is_restored(self):
        self.assertEqual(match_ex_suffix("Meowscarada ex", "Maskagato"), "Maskagato ex")

    def test_names_that_already_match_are_untouched(self):
        self.assertEqual(match_ex_suffix("Charizard ex", "Glurak ex"), "Glurak ex")
        self.assertEqual(match_ex_suffix("Charizard", "Glurak"), "Glurak")


class NameBookTests(unittest.TestCase):
    def setUp(self):
        german = {"a1-001": "Bisasam", "a1-223": "Giovanni"}
        english = {"a1-001": "Bulbasaur", "a1-223": "Giovanni", "b3-001": "Giovanni", "b3-002": "Charizard"}
        self.book = NameBook(german, english, SpeciesNames(SPECIES))

    def test_exact_tcgdex_entry_wins(self):
        self.assertEqual(self.book.resolve("a1-001", "Bulbasaur", True), ("Bisasam", "tcgdex"))

    def test_trainer_names_are_learned_from_other_sets(self):
        self.assertEqual(self.book.resolve("b3-001", "Giovanni", False), ("Giovanni", "learned"))

    def test_tcgdex_names_get_their_ex_suffix_back(self):
        book = NameBook({"a4-001": "Maskagato"}, {"a4-001": "Meowscarada ex"}, SpeciesNames(SPECIES))

        self.assertEqual(book.resolve("a4-001", "Meowscarada ex", True), ("Maskagato ex", "tcgdex"))

    def test_pokemon_fall_back_to_species_names(self):
        self.assertEqual(self.book.resolve("b3-002", "Charizard", True), ("Glurak", "species"))

    def test_unknown_trainers_fall_back_to_english(self):
        self.assertEqual(self.book.resolve("b3-099", "Secret Gadget", False), ("Secret Gadget", "fallback"))

    def test_trainers_are_never_derived_from_species(self):
        self.assertEqual(self.book.resolve("b3-100", "Bulbasaur Fan Club", False)[1], "fallback")


if __name__ == "__main__":
    unittest.main()
