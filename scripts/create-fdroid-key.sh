#!/usr/bin/env bash
# Creates the key that signs the F-Droid repository index (not the APK key). Users pin its fingerprint when they add the
# repository, so keep it backed up: a new key means every user must add the repository again.
set -euo pipefail

keystore="${1:-$HOME/.pocketdeck/fdroid-repo.jks}"
if [ -e "$keystore" ]; then
  echo "A key already exists at $keystore; not touching it." >&2
  exit 1
fi
mkdir -p "$(dirname "$keystore")"
keytool -genkeypair -v -keystore "$keystore" -alias pocketdeck-repo -keyalg RSA -keysize 4096 -validity 10000
echo "Use one password for store and key. Secrets: FDROID_KEYSTORE_BASE64=$(base64 -w0 "$keystore" | cut -c1-12)... (run: base64 -w0 $keystore)"
