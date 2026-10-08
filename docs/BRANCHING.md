# Branching, versions and releases

## Branches
- `main` is the release branch: every commit on it is a released version. Nothing is committed to it directly.
- `develop` collects the next release. Nothing is committed to it directly either.
- `feature/<topic>` (also `fix/<topic>`, `docs/<topic>`) branches off `develop` and goes back into `develop` by pull request,
  **squash-merged** (one commit per feature, written like the commit messages in the log). Delete the branch after merging.
- `hotfix/<topic>` branches off `main` for a fix that cannot wait for the next release. It goes into `main` (a release) and is then
  merged back into `develop`.

A pull request into `develop` or `main` must pass the CI workflow (`./gradlew check` and a release build).

## Versions (SemVer 2.0.0)
`MAJOR.MINOR.PATCH`; builds from `develop` are release candidates `MAJOR.MINOR.PATCH-rc.N`.
- MAJOR: something the player must act on (a stored format that cannot be read any more, a removed feature).
- MINOR: a new feature or visible improvement. PATCH: a fix only.
- `version.properties` holds the **next release** (`1.0.0`), never an `-rc`. It is bumped once per release cycle, on the first change
  that decides the size of the bump.
- Every push to `develop` is a release candidate: the CI builds a signed `X.Y.Z-rc.N` APK (artifact, 30 days), N = commits on `develop`
  since `main`, so each merged feature is a new candidate. Local builds without `-Ppocketdeck.rc=N` are the release version itself.
- `versionCode` is derived (`major*1,000,000 + minor*10,000 + patch*100 + N` for `-rc.N`, `+99` for the final release), so
  `1.0.0-rc.1 < 1.0.0-rc.2 < 1.0.0` and a candidate updates over the previous one.
- Tags are `v<version>` (final versions only), created by hand on `main`.

## Releasing
1. Test the latest release candidate from `develop`. When it is good, move the
   "Unreleased" part of `CHANGELOG.md` under the version (by pull request into `develop`).
2. Open a pull request `develop` -> `main` and **merge it with a merge commit** (not squash, or the two branches diverge).
3. Tag the merge commit on `main` by hand: `git tag -a v<version> -m "PocketDeck <version>" && git push origin v<version>`.
4. On GitHub, create the release for that tag by hand (notes from `CHANGELOG.md`) and publish it.
5. Publishing starts the release workflow: it checks that the tag matches `version.properties`, runs `./gradlew check`, signs the APK from
   the repository secrets and attaches the APK and its SHA-256 to the release.
6. For a final release (not a pre-release) the workflow then adds the APK to the F-Droid repository on the `gh-pages` branch, rebuilds and
   signs the index, and pushes it; GitHub Pages serves it at `https://codestev.github.io/PocketDeck/repo`. Nothing to do by hand.

Repository secrets: `SIGNING_KEYSTORE_BASE64` (`base64 -w0 release.jks`), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`; for F-Droid (`scripts/create-fdroid-key.sh`, a different key from the APK key, same password for store and key):
`FDROID_KEYSTORE_BASE64`, `FDROID_KEYSTORE_PASSWORD`, `FDROID_KEY_ALIAS` (`pocketdeck-repo`). Back that key up too: users pin its fingerprint.
GitHub Pages must be switched on for the `gh-pages` branch (a private repository needs a paid plan for that; a public one does not). See `docs/RELEASE.md` for creating and backing up the key.

## Repository settings (GitHub, set once)
Default branch `develop`; protect `main` and `develop` (pull request required, status check `check` required, no force pushes);
allow squash merging for `develop`, merge commits for `main`.
