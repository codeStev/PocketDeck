"""Tests for build-site.py. Run with `python3 -m unittest discover -s scripts -p 'test_*.py'` (needs `pip install markdown segno`)."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("build_site", Path(__file__).with_name("build-site.py"))
build_site = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(build_site)

FINGERPRINT = "82BFA899F2D14D82706573519DA5FCD87449C0B95FDBB2AE603AD09147BB663E"


class BuildSiteTest(unittest.TestCase):
    def test_sections_keep_their_names_and_text(self):
        preamble, parts = build_site.sections("# Title\nintro\n## One\na\n## Two\nb\n")

        self.assertIn("intro", preamble)
        self.assertEqual(["One", "Two"], list(parts))
        self.assertTrue(parts["Two"].startswith("## Two"))

    def test_markdown_links_to_files_of_the_repository_become_site_links(self):
        rendered = build_site.to_html("See [the policy](PRIVACY.md) and `NOTICE.md`.")

        self.assertIn('href="privacy/"', rendered)
        self.assertIn("blob/main/NOTICE.md", rendered)

    def test_the_install_block_shows_the_fingerprint_a_qr_code_and_the_repository_address(self):
        block = build_site.install_block("1.0.1", FINGERPRINT.lower())

        self.assertIn("82 BF A8 99", block)
        self.assertIn("<svg", block)
        self.assertIn("https://codestev.github.io/PocketDeck/repo", block)
        self.assertIn(f"fdroidrepos://codestev.github.io/PocketDeck/repo?fingerprint={FINGERPRINT}", block)

    def test_the_site_has_three_pages_and_the_landing_page_leaves_out_the_build_instructions(self):
        with tempfile.TemporaryDirectory() as out:
            build_site.write(Path(out), "index.html", build_site.landing("1.0.1", FINGERPRINT))
            landing = (Path(out) / "index.html").read_text(encoding="utf-8")

        self.assertIn("<h2>Install</h2>", landing)
        self.assertNotIn("./gradlew", landing)
        self.assertEqual(1, landing.count("<h2>Install</h2>"))


if __name__ == "__main__":
    unittest.main()
