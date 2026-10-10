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

# Same handling as create-release-key.sh: one password for store and key, asked once, stray whitespace removed.
read -r -s -p "Choose the key password (used for the store and the key; paste is fine): " password; echo
read -r -s -p "Again: " again; echo
password="$(printf '%s' "$password" | tr -d '[:space:]')"
again="$(printf '%s' "$again" | tr -d '[:space:]')"
if [ "$password" != "$again" ] || [ "${#password}" -lt 6 ]; then
  echo "The two entries differ, or the password has fewer than 6 characters. Nothing was created." >&2
  exit 1
fi

export POCKETDECK_KEY_PASSWORD="$password"
# The certificate name is public (anyone can read it from the index), so it is the handle, not a real name.
keytool -genkeypair -v -keystore "$keystore" -alias pocketdeck-repo -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=codeStev, O=codeStev" \
  -storepass:env POCKETDECK_KEY_PASSWORD -keypass:env POCKETDECK_KEY_PASSWORD
echo "Created $keystore (alias pocketdeck-repo). Back it up with its password, off this machine."
