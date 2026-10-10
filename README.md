# PocketDeck

PocketDeck is an unofficial Android companion for **Pokémon TCG Pocket**. It scans your collection from the game's "My Cards"
screen and suggests competitive decks you can build from the cards you own.

> Unofficial fan tool. Not affiliated with or endorsed by The Pokémon Company, Nintendo, Creatures, GAME FREAK or DeNA. Pokémon,
> Pokémon TCG Pocket, card names and artwork are trademarks and copyrights of their owners.
>
> Despite the similar name, PocketDeck is not affiliated with the GitHub organization [PocketDecks](https://github.com/PocketDecks), whose card
> data repository it uses.

## What it does
- **Scan:** Read your collection straight from the game's My Cards screen, with the scrolling done by you or by an optional auto-scroll ([details](PRIVACY.md#auto-scroll)).
- **Decks:**
  - Recent tournament decks, ranked by how well you can build them, with stand-ins for missing cards
  - Decks built from your own cards
  - Opening odds for each deck
  - "What to chase": the cards you lack that would finish the most decks
  - Optional suggestions from an AI service you set up
- **Import into the game:** Every deck comes with the game's import QR code, so you can load it into the game right away.
- **Everything on your phone:**
  - Several accounts
  - English and German
  - Light and dark theme
  - Your collection and settings stay on your phone

## Install
Releases are on the [releases page](https://github.com/codeStev/PocketDeck/releases) as signed APKs with their SHA-256 (`SHA256SUMS.txt`).
For automatic updates add the repository `https://codestev.github.io/PocketDeck/repo` in F-Droid (or Droid-ify, Neo Store) under
Settings > Repositories, and check that the fingerprint shown matches the one printed on the releases page. Without F-Droid, install the new
APK over the old one; it updates in place and keeps your data. The app is not on the Play Store.

## Built with AI assistance
PocketDeck was built with AI coding assistance ([Claude Code](https://claude.com/claude-code)).

## Auto-scroll (optional)
A scan reads your cards while the game's My Cards list moves past, so the list has to be scrolled from top to bottom. By default you
do that yourself. If you switch on auto-scroll, PocketDeck swipes the list for you instead. It is off until you agree in the app, and it
uses an Android Accessibility service only to swipe the card list while a scan runs; it reads nothing on the screen. Automating input in a
game may go against the game's terms of service, so you use it at your own risk.

## Privacy
No account, no analytics, no server of ours. See [PRIVACY.md](PRIVACY.md) for what stays on the phone, what is downloaded and what
the AI feature sends.

## Build
Needs JDK 25 (`JAVA_HOME=/usr/lib/jvm/java-25-openjdk` if your default is older) and the Android SDK.
```
./gradlew check                   # ktlint, Android Lint, architecture tests, unit tests
./gradlew :app:assembleDebug
```
The architecture is hexagonal (ports and adapters), enforced by tests; see `CLAUDE.md` for the layout, the data tools in `tools/` and
how the design system in `designsystem/` is used. Branches and versions (gitflow, SemVer) are in `docs/BRANCHING.md`, releasing in `docs/RELEASE.md`.
Found a bug? Use *Report a problem* in the app (Settings), which opens the [issue tracker](https://github.com/codeStev/PocketDeck/issues).

## Data and credits
- **Card data:** [PocketDecks/pokemon-tcg-pocket-cards](https://github.com/PocketDecks/pokemon-tcg-pocket-cards) (AGPL-3.0-or-later), scraped from Limitless TCG
- **Tournament decks:** [Limitless TCG](https://play.limitlesstcg.com)
- **Card images and German names:** [TCGdex](https://tcgdex.dev)

The full list is in `NOTICE.md`.

## License
GNU Affero General Public License v3.0 or later, see `LICENSE`. The bundled card dataset is AGPL-3.0-or-later, so the app as a whole is
distributed under the same terms.
