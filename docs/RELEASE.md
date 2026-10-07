# Releasing PocketDeck

## Build a release
```
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
./scripts/create-release-key.sh        # once; writes ~/.pocketdeck/signing.properties (outside the repository)
./gradlew check :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk
```
Without `~/.pocketdeck/signing.properties` the release build is unsigned (`app-release-unsigned`-style output); sign it
with `apksigner` or create the key first. The release build is minified with R8, resources are shrunk, and
`Log.v/d/i` calls are removed (`app/proguard-rules.pro`).

Bump `versionCode` (must grow with every release) and `versionName` in `app/build.gradle.kts`.

## Try a release build next to the real app
`./gradlew :app:assembleRelease -Ppocketdeck.signWithDebugKey=true -Ppocketdeck.idSuffix=.rc` builds "PocketDeck RC"
(`dev.pocketdeck.rc`) with its own data, so it can be installed beside the real app. R8 breaks things at run time
(reflection, serialization, Room, Hilt), so open every tab once: Collection, Decks (both tabs, tournament refresh, build, an
AI request), Scan, Settings (picker). **A release signed with the release key cannot update an install signed with the
debug key**; Android demands an uninstall first, which deletes the collection (rescan afterwards). Debug builds stay usable
for the developer's own phone.

## Direct release (GitHub release, own F-Droid repository)
- Publish the signed APK, its SHA-256 and a changelog. Keep the signing key; the same key must sign every update.
- Nothing from the Play policies applies, but see the blockers below.

## Play Store (later) additionally needs
- A Play developer account; a personal account created after 13 Nov 2023 needs a closed test with 12 testers opted in for
  14 days before production.
- An Android App Bundle (`./gradlew :app:bundleRelease`), Play App Signing, target API >= 36 from 31 Aug 2026 (the app
  targets 37), listing texts and screenshots, content rating, privacy policy URL, Data Safety form.
- The **Accessibility declaration** with an in-app disclosure (the consent dialog exists) for the auto-scroll service;
  automation tools are not accepted as accessibility tools, so rejection is possible.
- The foreground service declaration for `mediaProjection` and a justification for the overlay permission.

## Blockers to settle before any public release
1. **Data licences.** The card database is built by `tools/master_dataset` from:
   - `chase-manning/pokemon-tcg-pocket-cards` (gameplay data: attacks, HP, effects): **no licence**; all rights are reserved
     by default. Get permission or rebuild the data from permissively licensed sources (TCGdex also has the TCG Pocket
     cards' attacks, HP and effects).
   - `flibustier/pokemon-tcg-pocket-database` (MIT), `tcgdex/cards-database` (MIT), PokeAPI (BSD-3-Clause): attribution
     needed (`NOTICE.md`).
   - `PocketDecks/pokemon-tcg-pocket-cards` is AGPL-3.0; the app only links to artwork it hosts. Hot-linking someone's
     bandwidth is not a good idea for a public app.
2. **Card images and names.** Card art, names and logos belong to The Pokémon Company, Nintendo, Creatures and GAME FREAK.
   Do not use "Pokémon" or the game's name as if licensed (app name, icon, store text); say that the app is unofficial.
3. **The game's terms.** The game forbids scripts and bots that modify the application (Article 17(viii)). Auto-scroll
   performs swipes in the game; the consent dialog says so. Public distribution makes this more visible.
4. **Limitless API.** The tournament data comes from the public Limitless API; check their terms for apps.
