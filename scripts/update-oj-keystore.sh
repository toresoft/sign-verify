#!/usr/bin/env bash
#
# Rebuilds the "OJ keystore" used by DSS to validate the signature of the EU
# List of Trusted Lists (LOTL). The keystore must contain the certificate(s)
# announced in the Official Journal (OJ) that sign the LOTL / its pivots.
#
# A keystore with no real OJ certificate (e.g. a development placeholder) makes
# every pivot fail with INDETERMINATE/NO_CERTIFICATE_CHAIN_FOUND, so no Trusted
# List is loaded.
#
# Usage:
#   scripts/update-oj-keystore.sh [CERT_DIR] [KEYSTORE_PATH]
#
#   CERT_DIR       directory containing the OJ certificates as *.pem/*.crt/*.cer/*.der
#                  (default: ./oj-certs)
#   KEYSTORE_PATH  output PKCS#12 keystore
#                  (default: src/main/resources/keystore/oj-keystore.p12)
#
# The store password is taken from $OJ_KEYSTORE_PASSWORD (default: "changeit"),
# and MUST match app.security / APP_OJ_KEYSTORE_PASSWORD used at runtime.
#
# Where to get the certificates:
#   - The certificates announced in the OJ referenced by app.tsl.sources[].oj-url
#     (https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=uriserv:OJ.C_.2019.276.01.0001.01.ENG).
#   - The EU "LOTL" signing certificates published on https://esignature.ec.europa.eu
#     (Trusted List tooling) or bundled in the DSS distribution keystore
#     (esig/dss-demonstrations -> keystore.p12). Export them as PEM/DER into CERT_DIR.
#
set -euo pipefail

CERT_DIR="${1:-./oj-certs}"
KEYSTORE="${2:-src/main/resources/keystore/oj-keystore.p12}"
STOREPASS="${OJ_KEYSTORE_PASSWORD:-changeit}"

command -v keytool >/dev/null 2>&1 || { echo "ERROR: keytool not found on PATH" >&2; exit 1; }

if [[ ! -d "$CERT_DIR" ]]; then
  echo "ERROR: certificate directory '$CERT_DIR' does not exist." >&2
  echo "Create it and drop the OJ LOTL signing certificates (*.pem/*.crt/*.cer/*.der) inside." >&2
  exit 1
fi

shopt -s nullglob
certs=("$CERT_DIR"/*.pem "$CERT_DIR"/*.crt "$CERT_DIR"/*.cer "$CERT_DIR"/*.der)
if [[ ${#certs[@]} -eq 0 ]]; then
  echo "ERROR: no certificate files (*.pem/*.crt/*.cer/*.der) found in '$CERT_DIR'." >&2
  exit 1
fi

TMP_KS="$(mktemp -u).p12"
trap 'rm -f "$TMP_KS"' EXIT

count=0
for f in "${certs[@]}"; do
  alias="$(basename "${f%.*}")"
  keytool -importcert -noprompt -trustcacerts \
    -keystore "$TMP_KS" -storetype PKCS12 -storepass "$STOREPASS" \
    -alias "$alias" -file "$f"
  count=$((count + 1))
done

mkdir -p "$(dirname "$KEYSTORE")"
mv -f "$TMP_KS" "$KEYSTORE"
trap - EXIT

echo "Imported $count certificate(s) into $KEYSTORE"
echo "----"
keytool -list -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$STOREPASS"
echo "----"
echo "Done. Rebuild the image / restart the service so DSS picks up the new keystore."
