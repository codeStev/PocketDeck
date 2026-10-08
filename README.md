# PocketDeck

PocketDeck is an unofficial Android companion for **Pokémon TCG Pocket**. It scans your collection from the game's "My Cards"
screen and suggests competitive decks you can build from the cards you own.

> Unofficial fan tool. Not affiliated with or endorsed by The Pokémon Company, Nintendo, Creatures, GAME FREAK or DeNA. Pokémon,
> Pokémon TCG Pocket, card names and artwork are trademarks and copyrights of their owners.

## What it does
- **Scan** your collection while you scroll through My Cards (3 cards per row), or let the optional auto-scroll do the swiping.
- **Decks:** recent tournament decks ranked by how well you can build them, with stand-ins for missing cards; decks built from your own
  cards; optional suggestions from an AI service you set up; opening odds for each deck; "What to chase" for the cards you lack.
- **Import into the game:** each deck is shown as the game's import QR code.
- Several accounts, English and German, light and dark theme. Everything is stored on your phone.

## Install
Releases are on the [releases page](https://github.com/codeStev/PocketDeck/releases) as signed APKs with their SHA-256.
Once published, an own F-Droid repository gives automatic updates: `https://codestev.github.io/PocketDeck/repo`.
The app is not on the Play Store.

## Auto-scroll
Auto-scroll is optional and off until you agree in the app. It uses an Android Accessibility service only to swipe the card list while
a scan runs; it reads nothing on the screen. Automating input in a game may go against the game's terms of service, so you use it
at your own risk. Without it you scroll yourself and the app still reads the screen.

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
how the design system in `designsystem/` is used. Releasing is described in `docs/RELEASE.md`.

## Data and credits
Card data: [PocketDecks/pokemon-tcg-pocket-cards](https://github.com/PocketDecks/pokemon-tcg-pocket-cards) (AGPL-3.0-or-later), scraped
from Limitless TCG; tournament decks: [Limitless TCG](https://play.limitlesstcg.com); card images and German names:
[TCGdex](https://tcgdex.dev). Full list in `NOTICE.md`.

## Licence
GNU Affero General Public License v3.0 or later, see `LICENSE`. The bundled card dataset is AGPL-3.0-or-later, so the app as a whole is
distributed under the same terms.
