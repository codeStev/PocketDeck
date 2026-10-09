# Changelog

All notable changes, newest first. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/) (see `docs/BRANCHING.md`).

## [Unreleased]

## [1.0.0]
First public release.

### Added
- Scan your collection from the game's My Cards screen while you scroll, or with the optional auto-scroll (accessibility service, opt-in).
  The scan tells you when the game is not on its card list.
- Collection: search, owned-only, filters by set, rarity and type, sorting by number, name, copies or rarity (ascending or descending),
  card details with a copy stepper; several accounts.
- Decks: recent tournament decks ranked by how well you can build them (with effect-aware stand-ins), decks built from your own cards, optional
  suggestions from your own Claude, OpenAI or OpenRouter key or from a server of your own (Ollama, LM Studio, ...), opening odds, "What to chase", saved decks, import QR codes for the game.
- Share a deck as text or as a branded image tinted by its energy type.
- Export and import your collection as JSON or CSV; undo an import from the scan backup.
- Welcome tour, About screen with credits and licences, diagnostics you can copy when reporting a problem.
- English and German, light and dark theme.

### Notes
- Everything is stored on the phone; the only network calls are to Limitless TCG, TCGdex, GitHub (card images) and, if you set it up, your AI
  service. See `PRIVACY.md`.
- Tournament data is fetched politely: one tournament at a time, finished tournaments are kept on the phone, rate limits are honoured.
