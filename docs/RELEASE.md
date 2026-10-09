# Releasing PocketDeck

## The normal way: gitflow and the workflows
Branches, versions and the release steps are in `docs/BRANCHING.md`. In short: features go into `develop` (every push builds a signed
release candidate `X.Y.Z-rc.N` in CI), `release/X.Y.Z` is merged into `main`, the owner tags `vX.Y.Z` and publishes the GitHub release,
and the release workflow attaches the signed APK and its SHA-256 and adds the version to the F-Droid repository. The version lives in
`version.properties` only.

### One-time setup
1. `./scripts/create-release-key.sh` (APK key, writes `~/.pocketdeck/signing.properties`) and `./scripts/create-fdroid-key.sh` (signs the
   F-Droid index). Both use the certificate name `CN=codeStev, O=codeStev`. **Back up both `.jks` files with their passwords off this machine**:
   a lost APK key means the app can never be updated; a new F-Droid key means every user adds the repository again.
2. Repository secrets (Settings > Secrets and variables > Actions): `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`,
   `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS` (`pocketdeck`), `FDROID_KEYSTORE_BASE64`, `FDROID_KEYSTORE_PASSWORD`, `FDROID_KEY_ALIAS`
   (`pocketdeck-repo`). Set the keystores with `gh secret set NAME < <(base64 -w0 file.jks)`.
3. GitHub settings: default branch `develop`, branch protection, Pages from the `gh-pages` branch (after the first release created it).
4. **The first release candidate needs a clean install.** A release-signed build cannot update a debug-signed install; Android demands an
   uninstall first, which deletes the data. Export the collection (Settings > Your data) before, import it afterwards.

### Building a release locally
```
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
./gradlew check :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk
python3 scripts/licence-scan.py         # licences of the libraries in it
```
Without `~/.pocketdeck/signing.properties` the release build is unsigned; the release build is minified with R8, resources are shrunk and
`Log.v/d/i` calls are removed (`app/proguard-rules.pro`). `-Ppocketdeck.rc=N` makes it the release candidate `X.Y.Z-rc.N`.

### What CI checks
`check` and the unsigned release build, the licence scan, and a smoke test of the R8 build on an emulator (`scripts/smoke-test.py`: install,
welcome tour, every tab, no crash). A release candidate is only built when both are green. The smoke test does not cover Decks' sub-tabs,
the AI requests or a scan; open those once on a release candidate by hand (see the next section).

## Try a release build next to the real app
`./gradlew :app:assembleRelease -Ppocketdeck.signWithDebugKey=true -Ppocketdeck.idSuffix=.rc` builds "PocketDeck RC"
(`io.github.codestev.pocketdeck.rc`) with its own data, so it can be installed beside the real app. R8 breaks things at run time
(reflection, serialization, Room, Hilt), so open every tab once: Collection, Decks (both tabs, tournament refresh, build, an
AI request), Scan, Settings (picker). **A release signed with the release key cannot update an install signed with the
debug key**; Android demands an uninstall first, which deletes the collection (rescan afterwards). Debug builds stay usable
for the developer's own phone.

## Direct release (GitHub release, own F-Droid repository)
- The GitHub release carries the signed APK, its SHA-256 and the changelog section. Keep the signing key; the same key must sign every update.
- Nothing from the Play policies applies, but see the blockers below.
- Before the first release: read Limitless's terms and tell them about the app (blocker 4), and let someone listen to the app with TalkBack once.

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
   - `PocketDecks/pokemon-tcg-pocket-cards` (formerly `chase-manning/...`), `data/v5` (gameplay data: attacks, HP, effects):
     **AGPL-3.0-or-later** (checked 2026-10-08; its older v1-v4 data is MIT). Bundling it makes the app a work distributed under
     AGPL-3.0-or-later, so PocketDeck is released under that licence with its source public (`LICENSE`, `NOTICE.md`; link the
     source in the About screen). The data itself is scraped from Limitless TCG: the repository calls it facts, but read
     Limitless's terms once (see 4). The licence covers the data, not the card art or names (see 2).
   - `flibustier/pokemon-tcg-pocket-database` (MIT), `tcgdex/cards-database` (MIT), PokeAPI (BSD-3-Clause): attribution
     needed (`NOTICE.md`).
   - TCGdex also lists TCG Pocket cards (2,480 of our 4,317, not the newest 9 sets) and could replace the AGPL data for those
     sets; not needed while the app is AGPL. Six TCGdex values are wrong (a1-055, a1-071, b2a-081, b2a-125, b2a-130, pa-022).
   - The card and pack artwork that repository hosts is "for reference" and belongs to the game's owners.
2. **Card images and names.** Card art, names and logos belong to The Pokémon Company, Nintendo, Creatures and GAME FREAK.
   Naming the game the app works with in a description is common practice (other companion apps do), but it is not a licence:
   keep the name from reading as official, use no logos or Poké Ball marks, and say that the app is unofficial.
3. **The game's terms.** The game forbids scripts and bots that modify the application (Article 17(viii)). Auto-scroll
   performs swipes in the game; the consent dialog says so. Public distribution makes this more visible.
4. **Limitless API.** The tournament data comes from the public Limitless API; check their terms for apps. The app sends a User-Agent
   with the repository link, fetches one tournament at a time, keeps finished tournaments on the phone and stops at a rate limit until
   `Retry-After` has passed. The owner contacts Limitless before the first public release.
5. **Libraries (done 2026-10-09, enforced by CI).** `scripts/licence-scan.py` reads the licence of every library in the release build
   (225 at the time: Apache-2.0 for almost all, MIT for slf4j, BSD-3-Clause for the JSR-305 annotations). No Google Play services, Firebase
   or other proprietary library is in it, and nothing under a copyleft licence the AGPL cannot take in. CI runs the scan after the release build,
   so a new library with an unknown or unfit licence fails the build. Fonts (SIL OFL) and card data are listed in `NOTICE.md`.
