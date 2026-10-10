#!/usr/bin/env python3
"""Builds the project's GitHub Pages site (landing page, privacy policy, changelog) from the Markdown files in the repository.

    scripts/build-site.py OUT_DIR --version 1.0.1 --fingerprint <SHA-256 of the F-Droid repository key, hex>

The release workflow runs it after it has rebuilt the F-Droid index, and writes the result to the root of the `gh-pages`
branch next to `repo/`. Needs `markdown` and `segno` (pip). The pages are plain static HTML: the branch carries `.nojekyll`."""
import argparse
import html
import re
from pathlib import Path

import markdown
import segno

ROOT = Path(__file__).resolve().parent.parent
SITE = "https://codestev.github.io/PocketDeck"
REPO_URL = "https://github.com/codeStev/PocketDeck"
LANDING_SECTIONS = ["What it does", "Built with AI assistance", "Auto-scroll", "Privacy", "Data and credits", "Licence"]
LINKS = {
    "(PRIVACY.md)": "(privacy/)",
    "`NOTICE.md`": f"[NOTICE.md]({REPO_URL}/blob/main/NOTICE.md)",
    "`LICENSE`": f"[LICENSE]({REPO_URL}/blob/main/LICENSE)",
}

STYLE = """
:root { color-scheme: light dark; --ink: #1c1f26; --muted: #5b6270; --bg: #fbfaf8; --card: #ffffff; --line: #e3e0da; --accent: #c2410c; }
@media (prefers-color-scheme: dark) { :root { --ink: #eceef3; --muted: #a3a9b6; --bg: #14161b; --card: #1b1e25; --line: #2c303a; --accent: #ff8a5b; } }
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--ink); font: 17px/1.6 system-ui, -apple-system, "Segoe UI", sans-serif; }
header, main, footer { max-width: 46rem; margin: 0 auto; padding: 0 1rem; }
header { display: flex; flex-wrap: wrap; gap: .5rem 1.25rem; align-items: baseline; padding-top: 1.25rem; padding-bottom: .75rem; }
header a.brand { font-weight: 700; font-size: 1.15rem; color: var(--ink); text-decoration: none; margin-right: auto; }
nav a { color: var(--muted); text-decoration: none; margin-left: 1rem; }
nav a:hover, a { color: var(--accent); }
h1 { font-size: 1.9rem; line-height: 1.2; } h2 { margin-top: 2rem; font-size: 1.3rem; }
blockquote { margin: 1rem 0; padding: .1rem 1rem; border-left: 4px solid var(--line); color: var(--muted); }
code { font: .9em ui-monospace, monospace; background: var(--card); border: 1px solid var(--line); padding: .1em .35em; border-radius: 6px; overflow-wrap: anywhere; }
.install { background: var(--card); border: 1px solid var(--line); border-radius: 14px; padding: 1rem 1.25rem; margin: 1.5rem 0; }
.install h2 { margin-top: 0; } .install .qr { max-width: 11rem; float: right; margin: 0 0 .5rem 1rem; background: #fff; padding: .4rem; border-radius: 8px; }
.install .qr svg { width: 100%; height: auto; display: block; }
.fp { overflow-wrap: anywhere; } .button { display: inline-block; background: var(--accent); color: #fff; padding: .5rem 1rem; border-radius: 999px; text-decoration: none; font-weight: 600; }
footer { color: var(--muted); font-size: .85rem; padding-top: 2rem; padding-bottom: 2.5rem; }
"""


def sections(markdown_text):
    """Splits a document into its preamble and its `## ` sections, keeping the order."""
    parts = re.split(r"(?m)^## ", markdown_text)
    return parts[0], {part.split("\n", 1)[0].strip(): "## " + part for part in parts[1:]}


def to_html(markdown_text):
    for old, new in LINKS.items():
        markdown_text = markdown_text.replace(old, new)
    return markdown.markdown(markdown_text, extensions=["fenced_code", "tables", "sane_lists"])


def pretty(fingerprint):
    return " ".join(fingerprint[i : i + 2] for i in range(0, len(fingerprint), 2)).upper()


def install_block(version, fingerprint):
    """How to get the app: the F-Droid repository with its fingerprint and a QR code the client can read, or the APK."""
    link = f"fdroidrepos://{SITE.split('//')[1]}/repo?fingerprint={fingerprint.upper()}"
    qr = segno.make(link, error="m").svg_inline(scale=4, border=1, dark="#000000", light="#ffffff")
    return f"""
<section class="install" id="install">
<h2>Install</h2>
<div class="qr" role="img" aria-label="QR code that adds the F-Droid repository">{qr}</div>
<p>Version <strong>{html.escape(version)}</strong>. Add the repository in F-Droid, Droid-ify or Neo Store to get automatic updates:</p>
<p><code>{SITE}/repo</code></p>
<p>Check that the fingerprint the client shows is:</p>
<p class="fp"><code>{pretty(fingerprint)}</code></p>
<p>On a phone you can also <a href="{link}">open the repository link</a> directly.</p>
<p>Without F-Droid, download the signed APK (with its SHA-256) from the
<a class="button" href="{REPO_URL}/releases/latest">latest release</a>. A new APK installs over the old one and keeps your data.
The app is not on the Play Store.</p>
</section>
"""


def page(title, body, description):
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(description)}">
<style>{STYLE}</style>
</head>
<body>
<header>
<a class="brand" href="{SITE}/">PocketDeck</a>
<nav><a href="{SITE}/#install">Install</a><a href="{SITE}/privacy/">Privacy</a><a href="{SITE}/changelog/">Changelog</a><a href="{REPO_URL}">Source</a></nav>
</header>
<main>
{body}
</main>
<footer>Unofficial fan tool, not affiliated with or endorsed by The Pokémon Company, Nintendo, Creatures, GAME FREAK or DeNA.
Pokémon, Pokémon TCG Pocket, card names and artwork belong to their owners.</footer>
</body>
</html>
"""


def landing(version, fingerprint):
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    preamble, parts = sections(readme)
    kept = "\n".join(parts[name] for name in LANDING_SECTIONS if name in parts)
    description = "An unofficial Android companion for Pokémon TCG Pocket: scan your collection and get decks you can build."
    return page("PocketDeck", to_html(preamble) + install_block(version, fingerprint) + to_html(kept), description)


def write(out, relative, content):
    target = out / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("out", type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--fingerprint", required=True, help="SHA-256 of the repository's signing key, hex, with or without colons")
    args = parser.parse_args()
    fingerprint = args.fingerprint.replace(":", "")
    privacy = (ROOT / "PRIVACY.md").read_text(encoding="utf-8")
    changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    write(args.out, "index.html", landing(args.version, fingerprint))
    write(args.out, "privacy/index.html", page("Privacy - PocketDeck", to_html(privacy), "What PocketDeck stores, downloads and sends."))
    write(args.out, "changelog/index.html", page("Changelog - PocketDeck", to_html(changelog), "What changed in each PocketDeck release."))


if __name__ == "__main__":
    main()
