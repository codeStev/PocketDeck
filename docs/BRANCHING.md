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
`MAJOR.MINOR.PATCH`, optionally `-rc.N` for release candidates (`1.0.0-rc.1`, `1.0.0-rc.2`, `1.0.0`).
- MAJOR: something the player must act on (a stored format that cannot be read any more, a removed feature).
- MINOR: a new feature or visible improvement. PATCH: a fix only.
- The version lives in `version.properties` only. `versionName` is that string; `versionCode` is derived
  (`major*1,000,000 + minor*10,000 + patch*100 + N` for `-rc.N`, `+99` for a final release), so it always rises with the version.
- Tags are `v<version>`; each is created by the release workflow, never by hand.

## Releasing
1. On `develop` (via a pull request from a `release/<version>` branch or a feature branch), set `version.properties` and move the
   "Unreleased" part of `CHANGELOG.md` under the new version.
2. Open a pull request `develop` -> `main` and **merge it with a merge commit** (not squash, or the two branches diverge).
3. The push to `main` starts the release workflow: it checks the build, signs the APK from the repository secrets, tags `v<version>`,
   computes the SHA-256 and publishes the GitHub release (a version with `-` is marked pre-release). It fails if the tag already exists,
   i.e. when the version was not bumped.

Repository secrets: `SIGNING_KEYSTORE_BASE64` (`base64 -w0 release.jks`), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`. See `docs/RELEASE.md` for creating and backing up the key.

## Repository settings (GitHub, set once)
Default branch `develop`; protect `main` and `develop` (pull request required, status check `check` required, no force pushes);
allow squash merging for `develop`, merge commits for `main`.
