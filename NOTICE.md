# Notices

PocketDeck is licensed under the GNU Affero General Public License, version 3 or later (see `LICENSE`). Its source code
is public. The bundled card dataset (`app/src/main/assets/master_cards.json`) is built from the AGPL-3.0-or-later data below, so
the app as a whole is distributed under the same terms.

PocketDeck is an unofficial fan tool. It is not affiliated with or endorsed by The Pokémon Company, Nintendo, Creatures,
GAME FREAK or DeNA. Pokémon, Pokémon TCG Pocket, card names and artwork are trademarks and copyrights of their owners.

Data and formats used (see `docs/RELEASE.md` for what still has to be cleared before a public release):
- PocketDecks/pokemon-tcg-pocket-cards (originally chase-manning/pokemon-tcg-pocket-cards), `data/v5`: AGPL-3.0-or-later. Its card
  data is scraped from Limitless TCG (pocket.limitlesstcg.com); its own notices are in `THIRD_PARTY_NOTICES.md` of that repository
- flibustier/pokemon-tcg-pocket-database, MIT License
- tcgdex/cards-database (TCGdex), MIT License
- PokeAPI, BSD 3-Clause License
- KevinGutowski/tcgp-deck-qr, MIT License (the deck code format)
- Limitless (play.limitlesstcg.com) for tournament decklists

Open source libraries: Kotlin, AndroidX (Compose, Room, Lifecycle), Dagger Hilt, Ktor, kotlinx.serialization, Coil, ZXing,
OkHttp, kotlinx.coroutines, all under the Apache License 2.0 (ZXing: Apache 2.0).

Fonts bundled in the design system (`designsystem/src/main/res/font/`, licences in `designsystem/licenses/`): Fredoka
(Copyright 2016 The Fredoka Project Authors) and Nunito (Copyright 2014 The Nunito Project Authors), both under the SIL
Open Font License 1.1.
