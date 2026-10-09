#!/usr/bin/env bash
# Sets up git-flow (AVH edition: `git-flow-avh` / `gitflow-avh` package) for this clone with the branch names and the tag
# prefix docs/BRANCHING.md describes. The settings live in .git/config, so every clone runs this once.
set -euo pipefail

git flow init -d -f >/dev/null
git config gitflow.branch.master main
git config gitflow.branch.develop develop
git config gitflow.prefix.feature feature/
git config gitflow.prefix.release release/
git config gitflow.prefix.hotfix hotfix/
git config gitflow.prefix.versiontag v
echo "git-flow ready: main = releases, develop = next release, tags v<version>."
