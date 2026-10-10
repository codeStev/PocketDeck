"""Tests for build-site.py. Run with `python3 -m unittest discover -s scripts -p 'test_*.py'` (needs `pip install markdown segno`)."""
import importlib.util
import re
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

        self.assertIn('href="privacy/index.html"', rendered)
        self.assertIn("blob/main/NOTICE.md", rendered)

    def test_the_install_block_shows_the_fingerprint_a_qr_code_and_the_repository_address(self):
        block = build_site.install_block("1.0.1", FINGERPRINT.lower())

        self.assertIn("82 BF A8 99", block)
        self.assertIn("<svg", block)
        self.assertIn("https://codestev.github.io/PocketDeck/repo", block.replace("<wbr>", ""))
        self.assertIn(f"fdroidrepos://codestev.github.io/PocketDeck/repo?fingerprint={FINGERPRINT}", block)

    def test_the_bullets_of_what_it_does_become_tiles_titled_by_their_bold_lead(self):
        tiles = build_site.feature_tiles("## What it does\n- **Scan** your cards.\n- Plain bullet.\n")

        self.assertIn("<h3>Scan</h3><p>Your cards.</p>", tiles)
        self.assertIn("<h3>And more</h3><p>Plain bullet.</p>", tiles)

    def test_the_public_changelog_has_no_empty_unreleased_heading_and_no_format_brackets(self):
        text = "# Changelog\n\n## [Unreleased]\n\n## [1.0.1]\nFirst.\n\n## [1.0.0]\nOlder.\n"

        shown = build_site.public_changelog(text)

        self.assertNotIn("Unreleased", shown)
        self.assertIn("## 1.0.1\n", shown)
        self.assertIn("## 1.0.0\n", shown)
        self.assertEqual(shown, build_site.public_changelog(shown))

    def test_the_scan_tile_links_to_the_auto_scroll_part_of_the_privacy_page_which_has_that_anchor(self):
        landing = build_site.landing("1.0.1", FINGERPRINT)
        privacy = build_site.prose_page("Privacy", (build_site.ROOT / "PRIVACY.md").read_text(encoding="utf-8"), "d", "privacy")

        self.assertIn('href="privacy/index.html#auto-scroll"', landing)
        self.assertIn('id="auto-scroll"', privacy)
        self.assertNotIn("<h2>Auto-scroll", landing)

    def test_nested_bullets_become_a_list_inside_their_tile(self):
        tiles = build_site.feature_tiles("## What it does\n- **Decks:**\n  - First entry\n  - Second\n    entry\n- **Next:** Text.\n")

        self.assertIn("<h3>Decks</h3><ul><li>First entry</li><li>Second entry</li></ul>", tiles)
        self.assertIn("<h3>Next</h3><p>Text.</p>", tiles)

    def test_the_site_has_three_pages_and_the_landing_page_leaves_out_the_build_instructions(self):
        with tempfile.TemporaryDirectory() as out:
            build_site.write(Path(out), "index.html", build_site.landing("1.0.1", FINGERPRINT))
            landing = (Path(out) / "index.html").read_text(encoding="utf-8")

        self.assertEqual(1, landing.count("<h2>Install</h2>"))
        self.assertEqual(4, landing.count('class="tile '))
        self.assertNotIn("./gradlew", landing)
        self.assertIn("<h3>Scan</h3>", landing)
        for name in build_site.LANDING_SECTIONS:
            self.assertRegex(landing, rf"<h2[^>]*>{re.escape(name)}</h2>")

    def test_every_internal_link_of_the_site_points_at_a_file_that_exists(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            build_site.write(out, "index.html", build_site.landing("1.0.1", FINGERPRINT))
            build_site.write(out, "privacy/index.html", build_site.prose_page("Privacy", "# Privacy\n", "d", "privacy"))
            build_site.write(out, "changelog/index.html", build_site.prose_page("Changelog", "# Changelog\n", "d", "changelog"))
            build_site.copy_assets(out)

            broken = []
            for page in out.rglob("index.html"):
                for target in re.findall(r'(?:href|src)="([^"#]+)', page.read_text(encoding="utf-8")):
                    if re.match(r"^(https?|fdroidrepos|mailto):", target):
                        continue
                    if not (page.parent / target.split("?")[0]).resolve().is_file():
                        broken.append(f"{page.relative_to(out)} -> {target}")

        self.assertEqual([], broken)


if __name__ == "__main__":
    unittest.main()
