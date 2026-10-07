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
keytool -genkeypair -v -keystore "$keystore" -alias "$alias_name" -keyalg RSA -keysize 4096 -validity 10000

read -r -s -p "Store password (the one you just chose): " store_password; echo
read -r -s -p "Key password (the same if you pressed Enter at keytool): " key_password; echo

umask 077
cat > "$dir/signing.properties" <<PROPS
storeFile=$keystore
storePassword=$store_password
keyAlias=$alias_name
keyPassword=$key_password
PROPS
echo "Wrote $dir/signing.properties. Build a signed release with: ./gradlew :app:assembleRelease"
