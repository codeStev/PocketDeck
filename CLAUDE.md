# PocketDeck

Android app: scans the user's Pokémon TCG Pocket collection (screen capture + image recognition) and
suggests competitive decks buildable from owned cards. Personal/sideload use.

## Architecture: Hexagonal (Ports & Adapters) — non-negotiable
Dependency rule: `core:domain` <- `core:application` <- `adapter:*` <- `app`.

- `:core:domain` — entities, value objects, pure domain services. Pure Kotlin/JVM, no frameworks.
- `:core:application` — use cases (inbound ports + implementations) and outbound port interfaces.
  Depends only on domain and coroutines. No DI annotations.
- `:adapter:*` — one technology each (ui, capture, persistence, carddb, limitless, recognition).
  Depend only on `core:application`, **never on each other**.
- `:app` — composition root only (Hilt wiring). No business logic.
- `:architecture-test` — Konsist tests enforcing the rules above. They run in `./gradlew check`.

New external capability => new outbound port in `core:application` + new adapter module.
New way of driving the app => new driving adapter calling inbound ports.

## Clean code standards
- Ubiquitous language (Card, Collection, Deck, Archetype, Scan). Use cases are verb-noun; ports are named by
  role; adapters by technology + role (`RoomCollectionRepository`). No `Manager`/`Helper`/`Util` classes.
- One responsibility per class; functions do one thing (~20 lines max, <=3 parameters).
- Immutability: `data class`/`value class`, `val`, read-only collections.
- Expected failures are modelled as sealed result types; adapters translate technical exceptions at the
  boundary. Never swallow exceptions.
- No magic numbers; domain constants live in the domain (`Deck.SIZE`).
- Comments explain *why*. KDoc on every port interface.
- Time and randomness come through ports so tests are deterministic.
- Tests: domain and application use hand-written in-memory fakes of ports (no mocking own interfaces).
  Adapters get focused tests (Room DAO, Ktor MockEngine + JSON fixtures, recognizer + screenshot fixtures).

## Build & tooling
- Build JDK 25 (daemon JVM criteria pinned in `gradle/gradle-daemon-jvm.properties`); bytecode target Java 21.
  Run with `JAVA_HOME=/usr/lib/jvm/java-25-openjdk` if the system default is older.
- Versions live only in `gradle/libs.versions.toml`. Build configuration lives only in `build-logic`
  convention plugins (`pocketdeck.kotlin.jvm`, `.android.library`, `.android.application`, `.android.room`, `.quality`).
- `./gradlew check` must pass (ktlint, Android Lint with warnings as errors, Konsist, unit tests).
  `./gradlew ktlintFormat` fixes style. Warnings are errors in Kotlin compilation.
- Android modules use AGP 9 built-in Kotlin: do not apply `org.jetbrains.kotlin.android`.
- Detekt is intentionally absent until a Kotlin-2.4-compatible stable release exists.

## Testing on a device
- Unit tests run on the JVM (`./gradlew check`). Adapters with Android dependencies (`:adapter:persistence`) also
  have instrumented tests under `src/androidTest` that exercise real SQLite.
- `./gradlew :adapter:persistence:connectedDebugAndroidTest` currently reports "failing tests" with AGP 9.4.1's new
  android-test-engine although every test passes (verified in logcat and the result XML). Until that is resolved, run:
  `./gradlew :adapter:persistence:assembleDebugAndroidTest`, then
  `adb install -r -t adapter/persistence/build/outputs/apk/androidTest/debug/persistence-debug-androidTest.apk` and
  `adb shell am instrument -w dev.pocketdeck.adapter.persistence.test/androidx.test.runner.AndroidJUnitRunner`.
- Never send `adb shell input` events unless the same command first checks that `dev.pocketdeck` has window focus.

## Data tools (Python, `tools/*`; stdlib + numpy + Pillow; each has `python3 -m unittest discover -s tests -t .`)
- `tools/master_dataset/build_master.py` merges English v5 (gameplay data), TCGdex German (names, artwork), PokeAPI species
  names (derived German names) and flibustier (packs, rarity codes) into `app/src/main/assets/master_cards.json`. Every
  card records where its German name came from (`germanNameSource`: tcgdex | learned | species | fallback). Upstream name
  typos are repaired by canonical spelling. The app reads only this file (adapter `:adapter:carddb`), offline.
- `tools/card_index/build_index.py` builds `app/src/main/assets/card_index.bin`: a 48x28 grayscale thumbnail of every
  card's illustration. The Kotlin recognizer mirrors `cardindex/fingerprint.py`; the index header carries the parameters.
- `tools/digit_templates/build_templates.py` builds `adapter/recognition/src/main/resources/digit_templates.txt` (glyphs of
  the copy-count badge digits) from labelled real screenshots plus a synthetic fallback for every digit. Its tests need
  `POCKETDECK_FONT` (Poppins-Bold.ttf) and `POCKETDECK_FRAMES` (a folder with the labelled frames) and skip otherwise.
- Screenshots of the user's own game are fixtures only; keep them small in number (`adapter/recognition/src/test/resources`).

## How the game's "My Cards" screen behaves (measured on real screenshots, 1080x2340)
- Zoom levels: 3, 4 and 5 cards per row. Only the 3-per-row view shows copy counts (4-per-row shows none; 5-per-row
  showed none in our captures). The counts are a solid slate badge (about 76/92/116) at the tile's bottom-left; the game
  hides them while the list scrolls and shows them again shortly after it stops. A tile without a badge means "count
  unknown", never "1" (`TileRecognition.copies` is nullable).
- Tile aspect 0.7194; tile rows repeat at tile height + gap; the header above and the navigation bar below are
  background-coloured zones; a floating scroll button overlaps the bottom-right of the grid.
- Card names are German in-game; the illustration region is language independent, so matching uses illustrations only.
- Reprints with identical artwork cannot be told apart on screen; the recognizer deterministically picks the lowest id.

## Scanning flow (live)
- `ScanCaptureService` (MediaProjection, foreground) provides frames through the `ScreenCapturer` port; the application
  layer owns the loops: `ManualScanService` (feed frames while the player scrolls) and `AutoScanService` (rewind to the
  top, then page down with one row of overlap, waiting for the screen to rest and the count badges to fade in, until
  the list stops moving). Swipes go through the `GridScroller` port, implemented by `GridScrollService`, an
  AccessibilityService the player switches on in the system settings (gestures only, no screen reading).
- `SubmitScanFrame` returns a `FrameAnalysis` (tiles recognised, counts readable) which the auto-scroll policy uses.
- Logs: tag `PocketDeckScan` (per-frame analysis time, skipped frames).

## Decks (live)
- `:adapter:limitless` implements `MetaDecklistSource` with the public Limitless API (no key; `/tournaments?game=POCKET`,
  `/tournaments/{id}/standings`). Card ids are `set.lowercase().remove('-') + "-" + number.padStart(3,'0')`, which matches
  the master dataset for every listed card (checked on 1,171 live decklists). The adapter takes its `HttpClient` from `:app`.
- `ArchetypeAggregator` (domain) groups decklists into archetypes; `MetaBasedSuggestionStrategy` (application) picks per
  archetype the list closest to what the player owns; `SuggestDecks` combines all `DeckSuggestionStrategy` ports.
- Scan paging: `AutoScanService` measures the real scroll distance (`measureScrollShift`) and corrects the swipe length;
  swipes hold still at the end (`GridScrollService.drag`) because a flick makes the game's list glide on.
- One card read under different counts (each reading >= 0.8) is several prints with the same art: counts are added.
- Deck import codes (verified in the game by the user): `ExportDeckCodeService` writes base64 of `u8 n, n x u24be (trainer
  entity id + 10,000,000), u8 n, n x u24be (pokemon entity id), u8 n, n x u8 energy`, one entry per copy; energy 1-8 =
  grass, fire, water, lightning, psychic, fighting, darkness, metal. Entity ids (`gameEntityId`) come from the asset file
  name in the flibustier dataset. The Decks tab shows it as a QR (ZXing, version 9, error correction H) the game's
  "Scan Code" / "Scan Saved Image" reads.
- Substitution: `DeckSubstituter` (domain) replaces missing Pokémon only (same energy type, stage, evolution predecessor and
  ex status, spare copies, legal deck afterwards); trainers are never substituted because their effect is unknown to the
  model. `MetaBasedSuggestionStrategy` offers such a variant next to the printed list, scored 0.9 per swapped card lower.
