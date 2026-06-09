# Extra TLS intermediate certificates

These are **public CA intermediate certificates** imported into the JRE truststore
(`$JAVA_HOME/lib/security/cacerts`) at image build time (see `Dockerfile`).

They are needed because some national EU Trusted List (TSL) endpoints serve only
their leaf certificate over HTTPS, without the intermediate, so the JVM cannot
build the trust path and DSS fails to download those lists with
`PKIX path building failed`. The roots are already in the default truststore;
only the missing intermediates are added here.

| File | Subject | Issuer (root, already trusted) | Needed for |
|------|---------|--------------------------------|------------|
| `DigiCertGlobalG2TLSRSASHA2562020CA1.pem` | DigiCert Global G2 TLS RSA SHA256 2020 CA1 | DigiCert Global Root G2 | `eidas.gov.ie` (Ireland TSL) |

SHA-256 (DigiCert intermediate):
`C8025F9FC65FDFC95B3CA8CC7867B9A587B52779739579174 63FC813D0B625A9`

## Refresh / add a new one

To add the intermediate for another failing host:

```bash
host=example.tsl.host
# 1. read the leaf's AIA "CA Issuers" URL
openssl s_client -connect $host:443 -servername $host </dev/null 2>/dev/null \
  | openssl x509 -noout -ext authorityInfoAccess
# 2. download the intermediate it points to, convert to PEM
curl -s http://.../intermediate.crt | openssl x509 -inform DER -out docker/tls-certs/<name>.pem
# 3. add a COPY/keytool line (or it is picked up by the wildcard import) and rebuild
```
