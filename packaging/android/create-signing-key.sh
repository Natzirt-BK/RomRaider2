#!/usr/bin/env bash
set -euo pipefail

# Generate once, outside the repository. Never replace an existing signing identity.
[[ $# == 1 && "$1" = /* && "$1" != / ]] || {
    echo 'Usage: create-signing-key.sh /absolute/private/new-directory' >&2; exit 2;
}
signing_directory=$1
[[ ! -e "$signing_directory" ]] || { echo 'Signing directory already exists; preserving it.' >&2; exit 2; }
umask 077
mkdir -m 700 -- "$signing_directory"
openssl rand -base64 -out "$signing_directory/password" 48
keytool -genkeypair -keystore "$signing_directory/romraider2.p12" -storetype PKCS12 \
    -storepass:file "$signing_directory/password" -keypass:file "$signing_directory/password" \
    -alias romraider2 -keyalg RSA -keysize 3072 -validity 36500 \
    -dname 'CN=RomRaider2, O=NatZirt' -noprompt
keytool -exportcert -keystore "$signing_directory/romraider2.p12" \
    -storepass:file "$signing_directory/password" -alias romraider2 \
    -file "$signing_directory/certificate.der"
sha256sum "$signing_directory/certificate.der"
echo 'Keep the keystore and password together in a secure offline backup. Neither belongs in Git.'
