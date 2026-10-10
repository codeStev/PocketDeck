# Branching, versions and releases

## Branches (gitflow)
- `main` is the release branch: every commit on it is a released version, tagged `v<version>`. Nothing is committed to it directly.
- `develop` collects the next release. Nothing is committed to it directly either.
- `feature/<topic>` (also `fix/<topic>`, `docs/<topic>`) branches off `develop` and goes back into `develop` by pull request,
  **squash-merged** (one commit per feature, written like the commit messages in the log). Delete the branch after merging.
- `release/<version>` branches off `develop` when the next release is feature-complete. Only fixes, the changelog and (if the scope
  turned out different) the version go onto it. It is merged into `main` and back into `develop`, both with **merge commits**.
- `hotfix/<topic>` branches off `main` for a fix that cannot wait. It bumps the PATCH version, is merged into `main` (a release) and
  back into `develop`.

A pull request into `develop` or `main` must pass the CI workflow (`./gradlew check` and a release build).

## git-flow tooling
Install git-flow AVH (Arch: `gitflow-avh` from the AUR; Debian/Ubuntu: `apt install git-flow`) and run `scripts/gitflow-init.sh` once per clone.
Then the steps above are commands:

| Task | Command |
|---|---|
| Start a feature | `git flow feature start <topic>` |
| Finish it (one squashed commit on develop) | `git flow feature finish -S <topic>` |
| Start a release | `git flow release start 1.1.0` |
| Finish it (merge into main, tag `v1.1.0`, merge back into develop, delete the branch) | `git flow release finish -m "PocketDeck 1.1.0" 1.1.0` |
| Start / finish a hotfix | `git flow hotfix start 1.1.1` / `git flow hotfix finish -m "PocketDeck 1.1.1" 1.1.1` |

`finish` merges locally and you then push: `git push origin main develop --follow-tags`. With branch protection (pull requests required)
GitHub refuses that push to `main` and `develop`; then open the pull requests instead and use the tool only for `start`, or
keep protection off for the owner (an admin can bypass it). Features can still go by pull request and squash merge on GitHub.
Either way the tag is `v<version>` and the GitHub release is published by hand afterwards.

## Versions (SemVer 2.0.0)
`MAJOR.MINOR.PATCH`; builds from `develop` are release candidates `MAJOR.MINOR.PATCH-rc.N`.
- MAJOR: something the player must act on (a stored format that cannot be read any more, a removed feature).
- MINOR: a new feature or visible improvement. PATCH: a fix only.
- `version.properties` holds the **next release** (`1.0.0`), never an `-rc`. It is bumped once per release cycle, on the first change
  that decides the size of the bump.
- Every push to `develop`, `release/*` or `hotfix/*` is a release candidate: the CI builds a signed `X.Y.Z-rc.N` APK (artifact, 30 days), N = commits on the branch
  since `main`, so each merged feature is a new candidate. Local builds without `-Ppocketdeck.rc=N` are the release version itself.
- `versionCode` is derived (`major*1,000,000 + minor*10,000 + patch*100 + N` for `-rc.N`, `+99` for the final release), so
  `1.0.0-rc.1 < 1.0.0-rc.2 < 1.0.0` and a candidate updates over the previous one.
- Tags are `v<version>` (final versions only), created by hand on `main`.

## Releasing
1. Start: `git checkout -b release/1.1.0 develop && git push -u origin release/1.1.0`. `version.properties` already holds the version
   (it is set on `develop` right after the previous release, see below); change it here only if the scope differs. Move the "Unreleased"
   part of `CHANGELOG.md` under `## [1.1.0]`. Every push to the release branch builds a new candidate `1.1.0-rc.N`; fix what testing finds here.
2. Finish, once the last candidate is good:
   - Pull request `release/1.1.0` -> `main`, **merge commit**.
   - Tag that merge commit: `git checkout main && git pull && git tag -a v1.1.0 -m "PocketDeck 1.1.0" && git push origin v1.1.0`.
   - Pull request `main` -> `develop` (or `release/1.1.0` -> `develop`), **merge commit**, so fixes made on the release branch are not lost.
   - Delete the release branch.
3. On GitHub create the release for the tag by hand (notes from `CHANGELOG.md`) and publish it.
4. Publishing starts the release workflow: it checks that the tag matches `version.properties`, runs `./gradlew check`, signs the APK from
   the repository secrets and attaches the APK and its SHA-256 to the release.
5. For a final release (not a pre-release) the workflow then adds the APK to the F-Droid repository on the `gh-pages` branch, rebuilds and
   signs the index, and pushes it; GitHub Pages serves it at `https://codestev.github.io/PocketDeck/repo`. Nothing to do by hand.
6. Start the next cycle: on a branch off `develop` set `version.properties` to the next planned version (usually the next MINOR, `1.2.0`)
   and open a fresh "Unreleased" section in `CHANGELOG.md`.

### Hotfix
`git checkout -b hotfix/crash-on-start main`, fix, set `version.properties` to the PATCH bump (`1.1.1`), add the changelog entry. Pushes build
`1.1.1-rc.N`. Then merge into `main` with a merge commit, tag `v1.1.1`, publish the release as above, and merge `main` into `develop`
(on a conflict in `version.properties` keep develop's version).

Repository secrets: `SIGNING_KEYSTORE_BASE64` (`base64 -w0 release.jks`), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`; for F-Droid (`scripts/create-fdroid-key.sh`, a different key from the APK key, same password for store and key):
`FDROID_KEYSTORE_BASE64`, `FDROID_KEYSTORE_PASSWORD`, `FDROID_KEY_ALIAS` (`pocketdeck-repo`). Back that key up too: users pin its fingerprint.
GitHub Pages must be switched on for the `gh-pages` branch (a private repository needs a paid plan for that; a public one does not). See `docs/RELEASE.md` for creating and backing up the key.

## Repository settings (GitHub, set once)
Default branch `develop`; protect `main`, `develop` and `release/*` (pull request required, status check `check` required, no force pushes);
allow squash merging (for features into `develop`) and merge commits (for release, hotfix and back-merges).
