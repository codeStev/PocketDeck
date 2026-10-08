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

- `:designsystem` — the reusable Compose design system (package `dev.codestev.pocketdesign`). It depends on nothing of this
  project and no adapter but `:adapter:ui` may use it (both enforced by Konsist). The app's look is supplied to it as a
  brand (`PdBrand`).

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
- Emulators (AVDs `pd_small`, `pd_large`, `pd_tablet`, `pd_fold`, API 36 Google Play image) start headless with
  `$ANDROID_HOME/emulator/emulator -avd pd_large -no-window -no-audio -gpu swiftshader_indirect -port 5554`. With the phone and an
  emulator connected, every `adb` call needs `-s <serial>` (`emulator-5554`; the phone is the `192.168.2.96:<port>` one), or it
  hits the wrong device. The game itself does not render on these emulators (Unity graphics init fails with the software GPU, draws
  garbage with `-gpu host`), so scan frames cannot be recorded there.
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
- Substitution is effect-aware: cards carry attacks, ability and trainer text (master dataset -> `carddb` -> Room). The
  domain `EffectClassifier` maps the English effect text to `EffectTag`s (about 85% of the 871 distinct texts get a tag).
  `DeckSubstituter` accepts a Pokémon stand-in with the same stage, evolution predecessor and ex status that can pay for an
  attack with the deck's energy and brings >= 50% of the original's effect tags, tags the rest of the deck builds on
  (and their synergies, e.g. discard own energy <-> attach from discard) weighing 3x. A trainer needs the same kind and
  identical tags. Nothing matching => the card stays missing. `MetaBasedSuggestionStrategy` offers such a variant next
  to the printed list, scored 0.9 per swapped card lower; the UI shows the shared effects. A catalog stored without these
  details is reloaded (`CardCatalog.lacksCardDetails`).
- Heuristic builder (always available, needs no meta data or network): `HeuristicDeckBuilder` (domain) makes a legal
  20-card deck only from owned cards. It rates evolution lines (damage per energy, HP, ex, useful ability), keeps whole
  evolution lines with no more copies of an evolution than of its predecessor, fills trainers by effect (search/draw
  first), and picks the energy by trying every single type and pair. `HeuristicSuggestionStrategy` adds up to four decks
  of different kinds to the list; `BuildDeck` / `ListDeckCentrepieces` serve the "Build a deck" dialog (best deck, or
  around a chosen Pokémon). Built decks stay on top of the list until the app closes. Strategies run off the main
  thread (`SuggestDecksService` takes the coroutine context).
- The builder is calibrated with the tournament data (`CardPopularity`: share of decks that play a card, and `around(card)`:
  the share among decks that play the centrepiece). Measured by giving the builder a collection of the user's cards plus a
  real tournament deck and counting how much of that deck it recovers (118 live decks, leave-one-out so the tested deck is
  not in the data): without popularity 12.5% of the deck automatically and 17.5% around the deck's main Pokémon; with it
  45% and 53%. Weights (`CardRatings`: 6.0 for lines and trainers) were picked on that measure; going higher gave little.
  Rerun such a measure before changing the rating rules (a throwaway JVM test in `:adapter:carddb` reading the real
  master dataset worked well; it needs live decklists and a copy of the collection).
- Opening odds in the builder were tried and rejected (2026-10-08, 40 live archetypes, same leave-one-out measure, collection =
  the user's cards + the tested deck): choosing among the builder's energy options by the strongest attackers' chance to attack by
  turn 3 drops the recovery from 47% to 27% automatically (and changes nothing around a chosen Pokémon, where the energy is fixed). The
  odds favour decks with cheap attackers, which is not what strong players play. Two things the measure showed: the builder's `rating`
  is capped at 1, so many energy options tie and the first in energy order wins; and ordering by the uncapped strength is worse
  (36.6%). Odds stay an explanation shown on each deck, not a ranking input. Do not retry without a better quality proxy.
- AI advisor (`:adapter:llm`): `AskAiForDeckService` (application) asks a `DeckAdvisor`, never trusts the answer (unknown ids,
  cards not owned, legality, selectable energy are checked) and sends problems back for up to 3 attempts.
  `AdvicePromptWriter` (application) writes the prompt once for every provider; `ConfiguredDeckAdvisor` tries the providers
  the player set up in their order and falls back when one is busy. `AnthropicDeckAdvisor` (Messages API, forced tool call,
  prompt caching on the collection) and `OpenRouterDeckAdvisor` (chat completions, function call or JSON in text; the
  free `openrouter/free` router is the default model, models are editable). Keys live in
  `SharedPreferencesAdvisorSettingsStore` (private storage, no backup). The user has tested the advisor against the
  real services on their phone (as of 2026-10-08); the automated tests still use mock engines only.
- Settings live in their own tab (`adapter/ui/settings`: `SettingsViewModel` + `SettingsScreen`); "AI" is the first section
  (keys, models, provider order; explicit Save). A new section is another composable in `SettingsContent`. The Decks tab only
  asks the AI and points to Settings when no key is set up.
- Trainer playability (`TrainerPlayability`, domain): many trainers only act on something specific ("[W] Pokémon", "[W] Energy
  attached", Pokémon with “Team Rocket” in their name, named Pokémon, Stage 2, Mega ex). The conditions are read from the
  English text into `Requirement`s; a trainer with unmet requirements is a dead card in that deck. Used by the heuristic
  builder (never picks one), the AI answer check (sent back as a problem), the stand-in search, and told to the AI in the
  prompt. Checked on the real data: it flags 0.75% of the trainer copies in 1,171 live tournament decks, mostly lists that
  have no energy recorded; meta suggestions are not filtered (strong players chose them).
- OpenRouter models: `openrouter/free` is avoided because it routes to any free model, including ones that think for minutes and
  never answer (seen: nemotron, cohere north-mini-code, finish reason `length`). Default (`AdvisorProvider.OPENROUTER.defaultModels`): Nemotron Super 120B (free, tools, 262k context; it thinks, so the request asks
  for low effort). Gemma 4 was tried first and dropped: its free capacity answered HTTP 429 every time. The player may type several
  models separated by commas; they are tried in order until one gives a deck. Free models change often: recheck OpenRouter's
  public `/api/v1/models` (free + tools + long context).
- Model pickers (Settings > AI, one per provider, `ModelPickerDialog`): `AdvisorModelCatalog` (port, `ProviderModelCatalog` dispatches)
  has `OpenRouterModelCatalog` (public `/api/v1/models`, prices in dollars per million tokens, routers state -1 = no price) and
  `AnthropicModelCatalog` (`GET /v1/models`, needs the key, which the picker takes from the form as typed, saved or not).
  `ListSuitableModelsService` (application) keeps models with tools and >= 100k context, drops the `openrouter/` routers and sorts
  models that answer directly before ones that think first. OpenRouter lists free models only until the player switches on "Include
  paid models" (then free first, and a paid model needs a stated price; each shows the price and `ModelCost.perDeck`, about 30,000
  tokens in and 3,000 out); several OpenRouter models are chosen in order, a Claude model is chosen alone (tap again = default).
  A model whose `reasoning` metadata has no `default_enabled` counts as thinking first (cohere north-mini-code and nemotron-lightning
  reported nothing and still thought for minutes); only an explicit `default_enabled: false` or no reasoning support counts as direct.
- The Decks tab has two tabs: Tournament (`SuggestionOrigin.META`, with the refresh button) and Generated (builder and AI decks,
  with the build button); `DeckKind` decides which tab a deck or a notice belongs to. Generated decks are tinted
  (tertiary for the builder, secondary for the AI) so they cannot be mistaken for tournament decks.
- Languages: English is the default resource set (`values/`), German is `values-de/`; both must stay complete (lint's
  MissingTranslation checks it). The app follows the phone's language; Settings > Language (Android 13+) sets Android's per-app
  language (`LocaleManager`, declared in `res/xml/locales_config.xml`). View models that take a `Language` are keyed by it in
  `MainActivity`, so card names switch with the language. Add a language: new `values-xx/` for both modules, a line in
  `locales_config.xml`, an `AppLanguageChoice` entry.
- Accounts (profiles): the player can run several game instances, each is a `Profile` (domain) kept by the `ProfileRepository`
  port (`RoomProfileRepository`; the active profile id lives in SharedPreferences `profiles`). `CollectionRepository` and
  `CollectionBackup` keep their signatures but the Room adapters follow the active profile (`flatMapLatest`), so use cases,
  scans and suggestions need no profile parameter and re-emit when the player switches. Schema 6 added `profile` and
  `profileId` to `owned_card`/`owned_card_backup` (hand-written `AddProfilesMigration`, since the primary key changed;
  existing data goes to profile 1 "Main"; `CreateFirstProfile` seeds new databases). `MigrationPathTest` counts hand-written
  `Migration(from, to)` as well as `AutoMigration`. The switcher (`ProfileSwitcher`) is the Scaffold's top bar on every tab, so
  the account a scan will write to is always visible. Deleting a profile deletes its collection; the last one cannot be deleted.
- Saved decks: the Decks tab has a third tab "Saved" (`DeckKind.SAVED`). A generated deck is kept with `SaveDeck` as a snapshot
  (`SavedDeckContent`: title, origin, print ids + copies, energy, rationale, time from a `java.time.Clock`); ownership is never
  stored, `ObserveSavedDecks` rebuilds the `DeckSuggestion` against the catalog and the current collection (cards the catalog no
  longer knows are left out). Saving the same cards twice keeps one entry. `SavedDeckRepository` follows the active profile
  (`RoomSavedDeckRepository`, table `saved_deck`, schema 7, cards as `SavedDeckCodec` strings) and a profile's decks are deleted
  with it. Only generated decks have a Save button; saved decks can be renamed, deleted (with confirmation) and exported as code.
- "What to chase": `ChaseRanker` (domain, `meta/`) ranks the cards the player is missing by how many tournament decklists they
  would finish. Only lists missing 1..`MAX_MISSING_COPIES` (4, a guess) copies count; a list's weight is its archetype's strength
  shared among its lists and split over the missing cards by missing copies. Prints count as one card. `Card.packs` comes from the
  master dataset (`packs`) through `carddb` and Room (schema 8, `PackCodec`); a catalog whose cards have no pack at all counts as
  stored by an older version (`CardCatalog.lacksCardDetails`). `RankCardsToChase` (application) feeds the dialog opened from the
  Tournament tab (`adapter/ui/chase`). Not yet measured on real decklists; stand-ins (`DeckSubstituter`) are ignored.
- UI conventions (reviewed on the real phone, 2026-10): tasks that are too big for a dialog box (chase list, deck builder) use
  `PdFullScreenPanel`; each deck card has one filled primary button (import code), everything else is a small secondary button; a
  tab's actions are pinned above its list (`ActionBar`); empty-state texts wait for `DecksUiState.loaded`. Domain enums are shown
  through string resources in composables (`CardDetail` -> `detailText`), never as hardcoded English words. Notices are `PdBanner`s
  (a rate limit is a warning, `MetaSourceFailure.RateLimited`). Screens are checked with `adb exec-out screencap` (no input needed);
  any tap first checks that `dev.pocketdeck` has window focus. The window has no system title bar (`Theme.PocketDeck`).
- Releases: see `docs/RELEASE.md` (signing key outside the repo, R8, test copy `-Ppocketdeck.idSuffix=.rc`, licence blockers).

## Design system (`:designsystem`, package `dev.codestev.pocketdesign`)
- Reusable and brand-agnostic: tokens (`PdColors` with categorical `tints` looked up by key, `PdTypography`, `PdShapes`,
  `PdSpacing`, `PdMotion`), the theme (`PocketDesignTheme(brand, darkTheme)`, read tokens through `PocketDesign.colors` etc.;
  it also configures Material 3 underneath; no dynamic colour) and `Pd*` components. Components take all text and content
  descriptions as parameters and never use a literal colour. `SignalBrand` is the reference brand (orange-red accent, ten
  tints named by hue); PocketDeck maps its Pokémon types onto those tint keys in `:adapter:ui`, never in the library.
- Design source of truth: the two private Claude artifacts (design system and screens, see memory `design-system-artifact`);
  local copies in `.claude/ds` and `.claude/canvas`. Change tokens there and in `SignalBrand` together.
- Light is the default theme, dark is designed. `PdColorContrastTest` fails the build if any text pair drops below 4.5:1 or
  any graphic below 3:1 in either theme.
- Fonts Fredoka and Nunito are bundled (variable TTF, SIL OFL, `designsystem/licenses/`), so it works offline.
- The catalog (`CatalogActivity`, debug builds only) shows every token and component in light and dark:
  `adb shell am start -n dev.pocketdeck/dev.codestev.pocketdesign.catalog.CatalogActivity`. Add every new component to it.
- Migrated to the library: shell (floating nav, account top bar), Decks, Collection, Scan, Chase, Builder, Settings and their
  dialogs, the scan overlay (plain Views with the brand's dark-theme values, since `:adapter:capture` may not use the library),
  the notification accent and the adaptive launcher icon (accent background, two cards with a dot, monochrome for themed icons).

## How a game starts (measured with the user, 2026; the opening model follows it)
- Turn 0 (setup): each player has 5 cards; the game forces a Basic into the starting hand (no mulligans). The first player lays out
  Basic Pokémon (one Active, up to three on the Bench), then the second player does. Nothing else may be played.
- From round 1 both players draw one card every turn. The first player's first turn has no Energy Zone energy, no attack and no
  evolving; the second player attaches energy, attacks and evolves on theirs. Rare Candy says "not during your first turn" (both).
- A Pokémon cannot evolve on the turn it was put into play or evolved. Only the Active Pokémon attacks. Retreating discards energy
  equal to the Retreat Cost (to the discard pile). One supporter per turn, any number of items.
- Energy Zone: the deck's energy types (chosen when building the deck, up to 3) come into the zone at random, one at a time. The
  energy that is there stays until it is attached (blocking the next one); only one zone energy can be attached per turn. Trainers
  and abilities that "take an Energy from your Energy Zone" create energy of the named type and do not use that one.
- Energy that leaves a Pokémon (attack self-discards such as Mega Rayquaza ex's Mega Burst, retreat) goes to the discard pile; decks
  rely on pulling it back (Dragonair's "attach an Energy from your discard pile to your Active [N] Pokémon", Flame Patch, Volkner).

## Opening model (domain `opening/`)
- `OpeningSimulator` plays a deck's first turns many times (seeded `Random`, so tests are deterministic) and returns an
  `OpeningReport`: `chanceToPlay`, `chanceToAttack` (ever, by turn), `chanceToAttackFully` (strongest attack), `chanceToAttackOn`
  (on that turn itself, which shows decks that drain their own energy). Per `TurnOrder` (`FIRST`, `SECOND`).
- Shown in the Decks tab: every deck card has an "Opening odds" button (`OpeningOddsSection`). `AnalyseDeckOpening` (application) runs
  `OpeningAnalyser` for both seats off the main thread, seeded by the deck so a deck always shows the same numbers; the view model
  does it once per deck (`DeckListing.deckIdOf`: same cards, same id, whatever the prints). 2,000 starts per plan and the two
  seats on two cores: about 2.4 s on the user's phone for a 5-Pokémon deck (5,000 starts on one core took 6-16 s).
- Strict readings: a trainer is playable once in hand; a Pokémon only once on the board (evolutions respect the timing rules; Rare
  Candy skips a stage); a Pokémon attacks only from the Active Spot, so retreat/switch count. Trainers are rated in a neutral plan;
  each Pokémon is rated in a plan built around its own evolution line (`Scenario.focus`).
- Effects are read from the English card texts: `HandEffectReader` (draw, random search by filter or name list, look at top N,
  hand reshuffles, Rare Candy, evolve-from-deck such as Quick-Grow Extract), `BoardEffectReader` (attach energy from the zone or
  the discard pile, move energy between Pokémon, own switches, retreat discounts), `AttackEffectReader` (energy an attack discards
  from itself). Measured on the master dataset: most texts left unread target the opponent or need damage/knock-out tracking. A text that is not fully understood gives null: the card is simply
  not credited with the effect.
- Standing effects (`PassiveEffectReader`, measured on 1,009 live decklists: every opening-relevant text of the lists is read now):
  Stadiums (Fragrant Forest fetches a Basic each turn, Hiking Trail draws up to 3, Rainbow Cave rerolls a useless zone energy, Peculiar
  Plaza lowers [P] retreat), Tools that lower retreat (Small Balloon, Inflatable Boat; put on the Active Pokémon when a retreat is planned),
  bench aura (Bombirdier), abilities that draw or fetch at the start or end of every turn (Suicune ex, Entei ex, Meloetta), doubled energy
  (Serperior), Miraidon ex (swap in and take all energy, only when the deck is built around it), Eevee (evolves at once while Active) and
  Mythical Slab.
- Not modelled yet: Professor Sada and Lusamine (they need the "Ancient" / "Ultra Beast" traits, which the dataset lacks, and Lusamine
  the opponent's points), Caterpie's evolve-at-the-end-of-the-opponent's-turn, damage scaling (Mega Burst's 50 per energy counts as
  ready, not as damage), healing, damage boosts and anything about the opponent. "Can attack" ignores damage, knock-outs and retreat from
  Active by the opponent.
- Checked on 1,009 live Limitless decklists (2026-10-08): the odds are plausible for the top archetypes (Mewtwo ex Gardevoir,
  Mega Rayquaza, Suicune ex Baxcalibur, ...), the self-draining Mega Rayquaza ex shows the gap between "has attacked" and "attacks
  that turn", and What to chase puts the staples first (X Speed finishes 210 of the lists a collection lacking promos could
  almost complete). It exposed one bug: a third of the lists record no energy, and the guess used Dragon (no such energy) so
  Dragon decks never attacked; `EnergyInference` now reads the energy from the attack costs.
- Assumptions to recheck against the game: the zone picks uniformly among the deck's types; the Basic guarantee is modelled as
  dealing again until the hand has one; policy is greedy (attach where it makes an attack payable, favour the focus line).
