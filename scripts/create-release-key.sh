#!/usr/bin/env bash
# Creates the key that signs PocketDeck releases and the file the build reads it from.
# Keep the .jks file and its passwords safe and backed up: every update of the app has to be signed with the same key,
# and a lost key means a new app that cannot update the old one.
set -euo pipefail

dir="$HOME/.pocketdeck"
keystore="$dir/pocketdeck-release.jks"
alias_name="pocketdeck"

if [ -e "$keystore" ]; then
  echo "A key already exists at $keystore; not touching it." >&2
  exit 1
fi

mkdir -p "$dir"
chmod 700 "$dir"

# The certificate name is fixed on purpose: it is readable by anyone who inspects the APK, so it is the public handle, not a real name.
# The password is asked here, not by keytool: a pasted password with a trailing space or newline made keytool's own "re-enter" prompt fail.
# Stray whitespace is stripped and the password is handed over through the environment, so it never shows in the process list.
read -r -s -p "Choose the key password (used for the store and the key; paste is fine): " password; echo
read -r -s -p "Again: " again; echo
password="$(printf '%s' "$password" | tr -d '[:space:]')"
again="$(printf '%s' "$again" | tr -d '[:space:]')"
if [ "$password" != "$again" ] || [ "${#password}" -lt 6 ]; then
  echo "The two entries differ, or the password has fewer than 6 characters. Nothing was created." >&2
  exit 1
fi

export POCKETDECK_KEY_PASSWORD="$password"
keytool -genkeypair -v -keystore "$keystore" -alias "$alias_name" -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=codeStev, O=codeStev" \
  -storepass:env POCKETDECK_KEY_PASSWORD -keypass:env POCKETDECK_KEY_PASSWORD

umask 077
cat > "$dir/signing.properties" <<PROPS
storeFile=$keystore
storePassword=$password
keyAlias=$alias_name
keyPassword=$password
PROPS
echo "Wrote $dir/signing.properties. Build a signed release with: ./gradlew :app:assembleRelease"
echo "Back up $keystore and the password together, off this machine."
