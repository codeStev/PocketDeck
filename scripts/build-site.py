#!/usr/bin/env python3
"""Builds the project's GitHub Pages site (landing page, privacy policy, changelog) from the Markdown files in the repository.

    scripts/build-site.py OUT_DIR --version 1.0.1 --fingerprint <SHA-256 of the F-Droid repository key, hex>

The release workflow runs it after it has rebuilt the F-Droid index, and writes the result to the root of the `gh-pages`
branch next to `repo/`. Needs `markdown` and `segno` (pip). The pages are plain static HTML (the branch carries `.nojekyll`) in
the look of the app: the Signal colours of `designsystem/.../SignalBrand.kt`, its two fonts (copied with their licences) and its
corner scale. Every internal link is relative and names the `index.html` file, so the pages also work from a folder on disk,
where a link to a directory would show its listing."""
import argparse
import html
import re
import shutil
from pathlib import Path

import markdown
import segno

ROOT = Path(__file__).resolve().parent.parent
SITE = "https://codestev.github.io/PocketDeck"
REPO_URL = "https://github.com/codeStev/PocketDeck"
FONTS = ROOT / "designsystem" / "src" / "main" / "res" / "font"
LICENCES = ROOT / "designsystem" / "licenses"
LANDING_SECTIONS = ["Built with AI assistance", "Auto-scroll (optional)", "Privacy", "Data and credits", "Licence"]
LINKS = {
    "[PRIVACY.md](PRIVACY.md)": "[the privacy policy](privacy/index.html)",
    "(PRIVACY.md)": "(privacy/index.html)",
    "`NOTICE.md`": f"[NOTICE.md]({REPO_URL}/blob/main/NOTICE.md)",
    "`LICENSE`": f"[LICENSE]({REPO_URL}/blob/main/LICENSE)",
}
TINTS = ["orange", "blue", "green", "purple"]

# The app's tokens (SignalBrand, PdShapes, PdSpacing, PdTypography); light is the default, dark follows the system.
STYLE = """
@font-face { font-family: "Fredoka"; src: url("FONTS/fredoka.ttf") format("truetype"); font-weight: 300 700; font-display: swap; }
@font-face { font-family: "Nunito"; src: url("FONTS/nunito.ttf") format("truetype"); font-weight: 200 1000; font-display: swap; }
:root {
  color-scheme: light dark;
  --page: #F3F5F9; --card: #FFFFFF; --sunken: #E8ECF3; --line: #D5DBE5; --line-strong: #727D8F;
  --ink: #131822; --muted: #4F5A6B; --accent: #CC3518; --on-accent: #FFFFFF; --accent-soft: #FDE9E4; --on-accent-soft: #8C2410;
  --warning-soft: #FFF1D6; --on-warning: #8A5A00; --focus: #1456D6;
  --orange: #FDE8DF; --blue: #E0EEFB; --green: #E4F3E6; --purple: #F4E4F8;
  --shadow: 0 1px 2px rgba(19,24,34,.06), 0 8px 24px rgba(19,24,34,.07);
  --display: "Fredoka", ui-rounded, system-ui, sans-serif; --text: "Nunito", system-ui, -apple-system, "Segoe UI", sans-serif;
}
@media (prefers-color-scheme: dark) { :root {
  --page: #0D1015; --card: #171B22; --sunken: #0A0C10; --line: #2A313C; --line-strong: #8995A8;
  --ink: #F1F4F9; --muted: #A9B3C2; --accent: #FF7455; --on-accent: #1A0B07; --accent-soft: #3A1B14; --on-accent-soft: #FFB5A3;
  --warning-soft: #35280B; --on-warning: #F3B442; --focus: #7FB1FF;
  --orange: #3A1D12; --blue: #10263A; --green: #16301A; --purple: #2E1536;
  --shadow: 0 1px 2px rgba(0,0,0,.5), 0 8px 24px rgba(0,0,0,.35);
} }
* { box-sizing: border-box; }
html { scroll-behavior: smooth; scroll-padding-top: 5rem; }
body { margin: 0; background: var(--page); color: var(--ink); font: 500 16px/1.6 var(--text); }
a { color: var(--accent); font-weight: 700; }
a:focus-visible, .button:focus-visible { outline: 3px solid var(--focus); outline-offset: 2px; }
.wrap { max-width: 46rem; margin: 0 auto; padding: 0 16px; }
.pill-nav { position: sticky; top: 12px; z-index: 5; display: flex; align-items: center; gap: 4px; margin: 12px auto 0; max-width: 46rem;
  padding: 6px 8px; background: var(--card); border: 1px solid var(--line); border-radius: 999px; box-shadow: var(--shadow); }
.pill-nav .brand { display: flex; align-items: center; gap: 8px; margin-right: auto; padding: 2px 10px 2px 2px; color: var(--ink);
  text-decoration: none; font: 600 18px var(--display); }
.pill-nav .brand svg { width: 32px; height: 32px; border-radius: 10px; }
.pill-nav a.link { padding: 8px 12px; border-radius: 999px; color: var(--muted); text-decoration: none; font-weight: 800; font-size: 15px; }
.pill-nav a.link:hover, .pill-nav a.link[aria-current] { background: var(--accent-soft); color: var(--on-accent-soft); }
@media (max-width: 30rem) { .pill-nav .brand span { display: none; } .pill-nav a.link { padding: 8px 10px; font-size: 14px; } }
.hero { text-align: center; padding: 56px 0 32px; }
.hero .icon { display: inline-block; width: 96px; height: 96px; border-radius: 26px; overflow: hidden; box-shadow: var(--shadow); }
.hero .icon svg { display: block; width: 100%; height: 100%; }
h1 { margin: 16px 0 8px; font: 700 clamp(34px, 8vw, 44px)/1.1 var(--display); }
h2 { margin: 48px 0 12px; font: 600 28px/34px var(--display); }
h3 { margin: 24px 0 8px; font: 600 20px/26px var(--display); }
.lead { max-width: 34rem; margin: 0 auto 24px; color: var(--muted); font: 600 17px/26px var(--text); }
.actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 12px; }
.button { display: inline-flex; align-items: center; justify-content: center; min-height: 48px; padding: 0 24px; border-radius: 999px;
  background: var(--accent); color: var(--on-accent); font: 800 16px var(--text); text-decoration: none; transition: transform 120ms; }
.button.secondary { background: transparent; color: var(--ink); border: 2px solid var(--line-strong); }
.button:active { transform: scale(.97); }
.notice { margin: 8px 0 0; padding: 12px 16px; border-radius: 14px; background: var(--warning-soft); color: var(--on-warning); font-size: 14px; line-height: 20px; }
.features { display: grid; gap: 12px; margin: 32px 0 0; grid-template-columns: 1fr; }
@media (min-width: 38rem) { .features { grid-template-columns: 1fr 1fr; } }
.tile { padding: 20px; border-radius: 20px; }
.tile h3 { margin-top: 0; }
.tile p { margin: 0; }
.tile.orange { background: var(--orange); } .tile.blue { background: var(--blue); } .tile.green { background: var(--green); } .tile.purple { background: var(--purple); }
.install { display: grid; gap: 16px 24px; grid-template-columns: 1fr; margin-top: 48px; padding: 24px; background: var(--card); border: 1px solid var(--line);
  border-radius: 28px; box-shadow: var(--shadow); }
@media (min-width: 38rem) { .install { grid-template-columns: 11.5rem 1fr; } }
.install h2 { margin: 0 0 8px; }
.qr { width: 11.5rem; max-width: 100%; padding: 8px; background: #fff; border-radius: 14px; align-self: start; }
.qr svg { display: block; width: 100%; height: auto; }
.install ol { margin: 8px 0 16px; padding-left: 1.2rem; }
code { font: 600 .9em ui-monospace, "SFMono-Regular", Menlo, monospace; background: var(--sunken); padding: .15em .45em; border-radius: 6px; overflow-wrap: anywhere; }
.fp { display: block; margin: 8px 0 16px; padding: 12px 14px; border-radius: 10px; letter-spacing: .02em; word-spacing: .15em; }
section.prose h2:first-child { margin-top: 48px; }
blockquote { margin: 16px 0; padding: 4px 16px; border-left: 4px solid var(--accent); background: var(--accent-soft); color: var(--on-accent-soft); border-radius: 0 14px 14px 0; }
.page { padding: 32px 0 0; } .page h1 { margin-top: 24px; }
table { border-collapse: collapse; } td, th { padding: 6px 12px; border-bottom: 1px solid var(--line); text-align: left; }
footer { margin-top: 64px; padding: 24px 0 48px; border-top: 1px solid var(--line); color: var(--muted); font-size: 14px; line-height: 20px; }
@media (prefers-reduced-motion: reduce) { html { scroll-behavior: auto; } .button { transition: none; } }
"""

ICON = """<svg viewBox="0 0 108 108" role="img" aria-label="PocketDeck" xmlns="http://www.w3.org/2000/svg">
<rect width="108" height="108" fill="#CC3518"/>
<path d="M48,68h-10a4,4 0 0 1 -4,-4v-28a4,4 0 0 1 4,-4h18a4,4 0 0 1 4,4v4" fill="none" stroke="#fff" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M52,40h18a4,4 0 0 1 4,4v28a4,4 0 0 1 -4,4h-18a4,4 0 0 1 -4,-4v-28a4,4 0 0 1 4,-4z" fill="#fff"/>
<path d="M61,53a5,5 0 1 0 0.001 0z" fill="#CC3518"/></svg>"""


def sections(markdown_text):
    """Splits a document into its preamble and its `## ` sections, keeping the order."""
    parts = re.split(r"(?m)^## ", markdown_text)
    return parts[0], {part.split("\n", 1)[0].strip(): "## " + part for part in parts[1:]}


def to_html(markdown_text):
    for old, new in LINKS.items():
        markdown_text = markdown_text.replace(old, new)
    return markdown.markdown(markdown_text, extensions=["fenced_code", "tables", "sane_lists"])


def inline(markdown_text):
    """One short Markdown text as HTML without the surrounding paragraph."""
    return re.sub(r"^<p>(.*)</p>$", r"\1", to_html(markdown_text.strip()), flags=re.S)


def breakable(address):
    """An address that may wrap after a slash instead of in the middle of a word."""
    return html.escape(address).replace("://", ":@@").replace("/", "/<wbr>").replace(":@@", "://")


def public_changelog(markdown_text):
    """The changelog as visitors should read it: no empty "Unreleased" heading, and versions without the format's brackets."""
    without_empty = re.sub(r"(?m)^## \[Unreleased\]\s*\n(?=## \[)", "", markdown_text)
    return re.sub(r"(?m)^## \[(.+?)\]", r"## \1", without_empty)


def pretty(fingerprint):
    return " ".join(fingerprint[i : i + 2] for i in range(0, len(fingerprint), 2)).upper()


def feature_tiles(section):
    """The bullets of "What it does" as tinted tiles: the bold lead of a bullet is the title of its tile."""
    body = section.split("\n", 1)[1]
    bullets = [re.sub(r"\s*\n\s+", " ", item).strip() for item in re.split(r"(?m)^- ", body)[1:]]
    tiles = []
    for index, bullet in enumerate(bullets):
        lead = re.match(r"\*\*(.+?)\*\*:?\s*(.*)", bullet, flags=re.S)
        title, text = (lead.group(1).rstrip(":"), lead.group(2)) if lead else ("And more", bullet)
        text = text[:1].upper() + text[1:]
        tiles.append(f'<div class="tile {TINTS[index % len(TINTS)]}"><h3>{html.escape(title)}</h3><p>{inline(text)}</p></div>')
    return '<div class="features">' + "".join(tiles) + "</div>"


def install_block(version, fingerprint):
    """How to get the app: the F-Droid repository with its fingerprint and a QR code the client can read, or the APK."""
    link = f"fdroidrepos://{SITE.split('//')[1]}/repo?fingerprint={fingerprint.upper()}"
    qr = segno.make(link, error="m").svg_inline(scale=4, border=1, dark="#000000", light="#ffffff")
    return f"""
<section class="install" id="install">
<div class="qr" role="img" aria-label="QR code that adds the F-Droid repository">{qr}</div>
<div>
<h2>Install</h2>
<p>Version <strong>{html.escape(version)}</strong>. For automatic updates add the repository in F-Droid, Droid-ify or Neo Store:</p>
<ol>
<li>Add this address as a repository (or scan the code): <code class="fp">{breakable(SITE + "/repo")}</code></li>
<li>Check that the fingerprint it shows is: <code class="fp">{pretty(fingerprint)}</code></li>
<li>Install PocketDeck and let it update itself.</li>
</ol>
<p><a href="{link}">Open the repository link on your phone</a>, or download the signed APK, with its SHA-256, from the releases. A new APK installs
over the old one and keeps your data. The app is not on the Play Store.</p>
<div class="actions" style="justify-content: flex-start"><a class="button" href="{REPO_URL}/releases/latest">Latest release</a></div>
</div>
</section>
"""


def nav(prefix, active):
    def link(target, label, key):
        current = ' aria-current="page"' if key == active else ""
        return f'<a class="link" href="{target}"{current}>{label}</a>'

    return f"""<nav class="pill-nav wrap" aria-label="Site">
<a class="brand" href="{prefix}index.html">{ICON}<span>PocketDeck</span></a>
{link(prefix + "index.html#install", "Install", "install")}{link(prefix + "privacy/index.html", "Privacy", "privacy")}{link(prefix + "changelog/index.html", "Changelog", "changelog")}<a class="link" href="{REPO_URL}">Source</a>
</nav>"""


def page(title, body, description, prefix="./", active=""):
    style = STYLE.replace("FONTS/", f"{prefix}assets/fonts/")
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(description)}">
<meta name="theme-color" content="#CC3518">
<style>{style}</style>
</head>
<body>
{nav(prefix, active)}
<main class="wrap">
{body}
</main>
<footer class="wrap"><p>Unofficial fan tool, not affiliated with or endorsed by The Pokémon Company, Nintendo, Creatures, GAME FREAK or DeNA.
Pokémon, Pokémon TCG Pocket, card names and artwork belong to their owners. Fonts: Fredoka and Nunito (SIL Open Font License).</p></footer>
</body>
</html>
"""


def hero():
    return f"""<div class="hero">
<span class="icon">{ICON}</span>
<h1>PocketDeck</h1>
<p class="lead">Scan your Pokémon TCG Pocket collection and get competitive decks you can build from the cards you own.</p>
<div class="actions"><a class="button" href="#install">Install</a><a class="button secondary" href="{REPO_URL}">Source on GitHub</a></div>
<p class="notice">Unofficial fan tool. Not affiliated with or endorsed by The Pokémon Company, Nintendo, Creatures, GAME FREAK or DeNA.</p>
</div>"""


def landing(version, fingerprint):
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    parts = sections(readme)[1]
    kept = "\n".join(parts[name] for name in LANDING_SECTIONS if name in parts)
    body = hero() + feature_tiles(parts["What it does"]) + install_block(version, fingerprint) + '<section class="prose">' + to_html(kept) + "</section>"
    description = "An unofficial Android companion for Pokémon TCG Pocket: scan your collection and get decks you can build."
    return page("PocketDeck", body, description, "./", "install")


def prose_page(title, markdown_text, description, key):
    return page(title, f'<article class="page prose">{to_html(markdown_text)}</article>', description, "../", key)


def write(out, relative, content):
    target = out / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def copy_assets(out):
    """The app's own fonts and their licences, so the pages look like the app and need no other site."""
    target = out / "assets" / "fonts"
    target.mkdir(parents=True, exist_ok=True)
    for source in [*FONTS.glob("*.ttf"), *LICENCES.glob("OFL-*.txt")]:
        shutil.copy(source, target / source.name)


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
    write(args.out, "privacy/index.html", prose_page("Privacy - PocketDeck", privacy, "What PocketDeck stores, downloads and sends.", "privacy"))
    write(args.out, "changelog/index.html", prose_page("Changelog - PocketDeck", public_changelog(changelog), "What changed in each PocketDeck release.", "changelog"))
    copy_assets(args.out)


if __name__ == "__main__":
    main()
