"""Generate the AGID / AGID_TS DSS validation policies from STANDARD.xml.

Run from the repository root:  python3 scripts/gen_agid.py
Regenerates src/main/resources/policy/AGID.xml and AGID_TS.xml (QES-strict,
Italian digital-signature law) with inline regulatory comments. Do not hand-edit
the generated files; change this script and re-run, then validate against the
DSS policy.xsd (dss-policy-jaxb).
"""

import re, pathlib

base = pathlib.Path("src/main/resources/policy")
std = (base / "STANDARD.xml").read_text()

# Regulation-grounded AgID profile (QES / firma digitale).
#   - eIDAS 910/2014 art. 3(12) + 26 + 32: QES = AdES on a qualified certificate, created by a QSCD.
#   - CAD (D.Lgs. 82/2005) art. 1(1)(s) + art. 24: firma digitale = qualified cert + QSCD.
#   - DPCM 22/02/2013 art. 28 + ETSI EN 319 412-2: nonRepudiation key usage mandatory.
#   - eIDAS art. 22 + ETSI TS 119 612: signing cert must resolve to a CA/QC granted trust service.
#   - eIDAS art. 25(1): mutual recognition -> accept other EU member states, only flag non-IT.
#   - AgID Regole Tecniche (crypto) + ETSI TS 119 312: SHA-256+, RSA>=2048, ECDSA P-256.
DESC_AGID = (
    "Italian qualified electronic signature / digital signature verification policy (AgID). "
    "Accepts ONLY qualified signatures: the signing certificate must be qualified (QcCompliance), "
    "created by a QSCD (QcSSCD), carry the nonRepudiation key usage and resolve to a CA/QC trust "
    "service granted on a Trusted List. Legal basis: eIDAS 910/2014 art. 3(12)/26/32, CAD art. 24, "
    "DPCM 22/02/2013 art. 28, ETSI EN 319 412-2/-5, ETSI TS 119 312. Non-IT EU member states are "
    "accepted (eIDAS art. 25 mutual recognition) and only flagged."
)
DESC_TS = DESC_AGID + " This variant additionally REQUIRES a valid T-level timestamp."

TRUSTSERVICE_REPL = (
    '\t\t\t<TrustServiceTypeIdentifier Level="FAIL">\n'
    "\t\t\t\t<Id>http://uri.etsi.org/TrstSvc/Svctype/CA/QC</Id>\n"
    "\t\t\t</TrustServiceTypeIdentifier>\n"
    '\t\t\t<TrustServiceStatus Level="FAIL">\n'
    "\t\t\t\t<Id>http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/undersupervision</Id>\n"
    "\t\t\t\t<Id>http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/accredited</Id>\n"
    "\t\t\t\t<Id>http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/supervisionincessation</Id>\n"
    "\t\t\t\t<Id>http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted</Id>\n"
    "\t\t\t\t<Id>http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/withdrawn</Id>\n"
    "\t\t\t</TrustServiceStatus>"
)


def header(with_ts: bool) -> str:
    title = (
        "PROFILO AGID-TS — FIRMA QUALIFICATA / DIGITALE CON MARCA TEMPORALE (ITALIA)"
        if with_ts
        else "PROFILO AGID — FIRMA ELETTRONICA QUALIFICATA / FIRMA DIGITALE (ITALIA)"
    )
    ts_line = (
        "    Marca temporale: OBBLIGATORIA. Richiede almeno un timestamp T-level valido\n"
        "    (TLevelTimeStamp = FAIL) — opponibilita' temporale ai sensi del DPCM art. 62.\n"
        if with_ts
        else "    Marca temporale: NON obbligatoria (validata se presente). Per renderla\n"
        "    obbligatoria usare il preset AGID-TS.\n"
    )
    return (
        "<!--\n"
        "  ============================================================================\n"
        f"  {title}\n"
        "  ============================================================================\n"
        "  Generato da scripts/gen_agid.py a partire da STANDARD.xml.\n"
        "  NON modificare a mano: rigenerare con lo script.\n"
        "\n"
        "  Base normativa:\n"
        "    - Regolamento (UE) 910/2014 (eIDAS), artt. 3(12), 25, 26, 32\n"
        "    - D.Lgs. 82/2005 (CAD), artt. 1(1)(s), 20, 21, 24\n"
        "    - DPCM 22 febbraio 2013, artt. 28, 35, 62\n"
        "    - AgID Regole Tecniche / Det. 157/2020 (algoritmi crittografici)\n"
        "    - ETSI EN 319 102-1 (processo di validazione AdES)\n"
        "    - ETSI EN 319 412-2/-3/-5 (profili certificati QC e TSA)\n"
        "    - ETSI TS 119 312 (suite crittografiche)\n"
        "    - ETSI TS 119 612 + Dec. (UE) 2015/1505 (Trusted List)\n"
        "\n"
        "  Perimetro: accetta SOLO firme qualificate (QES) / firma digitale.\n"
        "    Certificato qualificato (QcCompliance) su QSCD (QcSSCD), KeyUsage\n"
        "    nonRepudiation, servizio CA/QC 'granted' sulla Trusted List.\n"
        "    Formati: PAdES, CAdES, XAdES, JAdES, ASiC-S/E.\n"
        "    Trusted List IT: https://eidas.agid.gov.it/TL/TSL-IT.xml (in EU LOTL).\n"
        "\n"
        f"{ts_line}"
        "\n"
        "  Livelli di criticita':\n"
        "    FAIL   -> il controllo fallisce: la firma e' TOTAL-FAILED\n"
        "    WARN   -> avviso: la firma puo' risultare INDETERMINATE\n"
        "    INFORM -> informativo: non influisce sull'esito\n"
        "    IGNORE -> controllo disabilitato\n"
        "  ============================================================================\n"
        "-->\n"
    )


# (anchor substring, [comment lines without indentation]). Inserted before the FIRST line that
# contains the anchor (the main-signature block, where duplicates exist in counter-sig/timestamp).
COMMENTS = [
    (
        "<ContainerConstraints>",
        ["<!-- Contenitori ASiC-S / ASiC-E (ETSI EN 319 162-1). Altri contenitori: rifiutati. -->"],
    ),
    (
        "<PDFAConstraints>",
        ["<!-- Conformita' PDF/A: non imposta da AgID per la firma; abilitabile per conservazione. -->"],
    ),
    (
        "<SignatureConstraints>",
        [
            "<!-- ================================================================ -->",
            "<!-- VINCOLI SULLA FIRMA PRINCIPALE                                   -->",
            "<!-- ================================================================ -->",
        ],
    ),
    (
        "<AcceptableFormats Level=\"FAIL\">",
        ["<!-- Formati AdES accettati: tutti (PAdES/CAdES/XAdES/JAdES/ASiC). -->"],
    ),
    (
        "<TrustServiceTypeIdentifier Level=\"FAIL\">",
        [
            "<!-- Il certificato del firmatario deve risolversi a un servizio CA/QC sulla",
            "     Trusted List (eIDAS art. 22, ETSI TS 119 612): solo CA qualificate. -->",
        ],
    ),
    (
        "<TrustServiceStatus Level=\"FAIL\">",
        [
            "<!-- Status del servizio qualificato: 'granted' (attuale) piu' gli stati",
            "     storici, per validare firme apposte in passato. -->",
        ],
    ),
    (
        "<SigningCertificate>",
        ["<!-- ===== CERTIFICATO DEL FIRMATARIO ===== -->"],
    ),
    (
        "<NotExpired Level=\"FAIL\" />",
        ["<!-- Non scaduto al riferimento temporale (CAD art. 24 c. 4-bis). -->"],
    ),
    (
        "<RevocationDataAvailable Level=\"FAIL\" />",
        ["<!-- Dati di revoca (CRL/OCSP) obbligatori e accettabili (DPCM artt. 21-23). -->"],
    ),
    (
        "<KeyUsage Level=\"FAIL\">",
        [
            "<!-- nonRepudiation OBBLIGATORIO per firma qualificata/digitale",
            "     (DPCM 22/02/2013 art. 28; ETSI EN 319 412-2). -->",
        ],
    ),
    (
        "<QcCompliance Level=\"FAIL\" />",
        [
            "<!-- Certificato QUALIFICATO: QcStatement QcCompliance obbligatorio",
            "     (eIDAS art. 28; ETSI EN 319 412-5). -->",
        ],
    ),
    (
        "<QcSSCD Level=\"FAIL\" />",
        [
            "<!-- Chiavi su QSCD/SSCD: requisito di firma qualificata (eIDAS art. 3(12))",
            "     e 'firma digitale' (CAD art. 24; DPCM art. 35). Abbassare a WARN se",
            "     emergono QES valide per cui DSS non conferma il QSCD. -->",
        ],
    ),
    (
        "<QcLegislationCountryCodes Level=\"WARN\">",
        [
            "<!-- Codice paese del certificato: accetta tutti gli Stati UE (eIDAS art. 25,",
            "     riconoscimento reciproco), segnala soltanto se diverso da IT. -->",
        ],
    ),
    (
        "<CACertificate>",
        ["<!-- ===== CERTIFICATI CA INTERMEDI ===== -->"],
    ),
    (
        "<SignedAttributes>",
        ["<!-- Attributi firmati (CAdES SignedAttributes / XAdES SignedProperties). -->"],
    ),
    (
        "<SigningTime Level=\"FAIL\" />",
        [
            "<!-- Ora di firma dichiarata obbligatoria; per la validita' legale conta",
            "     comunque la marca temporale (livelli T/LT/LTA). -->",
        ],
    ),
    (
        "<UnsignedAttributes>",
        ["<!-- Attributi non firmati (marche temporali, dati di validazione). -->"],
    ),
    (
        "<CounterSignatureConstraints>",
        [
            "<!-- ================================================================ -->",
            "<!-- VINCOLI SULLA CONTROFIRMA (vincoli QES non applicati qui)        -->",
            "<!-- ================================================================ -->",
        ],
    ),
    (
        "<Timestamp>",
        [
            "<!-- ================================================================ -->",
            "<!-- MARCA TEMPORALE / TSA (ETSI EN 319 421/422; DPCM artt. 47-50)    -->",
            "<!-- ================================================================ -->",
        ],
    ),
    (
        "<ExtendedKeyUsage Level=\"FAIL\">",
        ["<!-- Certificato TSA: EKU id-kp-timeStamping (ETSI EN 319 412-3; DPCM art. 49). -->"],
    ),
    (
        "<Revocation>",
        [
            "<!-- ================================================================ -->",
            "<!-- DATI DI REVOCA (CRL / OCSP)                                      -->",
            "<!-- ================================================================ -->",
        ],
    ),
    (
        "<EvidenceRecord>",
        ["<!-- Evidence Record (RFC 4998/6283; ETSI EN 319 162) per conservazione. -->"],
    ),
    (
        '<Cryptographic Level="FAIL">',
        [
            "<!-- ================================================================ -->",
            "<!-- SUITE CRITTOGRAFICA (AgID Regole Tecniche; ETSI TS 119 312)      -->",
            "<!-- SHA-256+ e RSA>=2048 correnti; SHA-1, MD5 e RSA<2048 con data di  -->",
            "<!-- scadenza superata diventano non validi (post UpdateDate: WARN).  -->",
            "<!-- ================================================================ -->",
        ],
    ),
    (
        '<Model Value="SHELL" />',
        ["<!-- Modello di validazione SHELL (ETSI EN 319 102-1). -->"],
    ),
    (
        "<eIDAS>",
        ["<!-- Vincoli eIDAS sulla Trusted List: freschezza, firma, versione (TLv5/6). -->"],
    ),
    (
        '<TLevelTimeStamp Level="FAIL" />',
        [
            "<!-- MARCA TEMPORALE OBBLIGATORIA: almeno un timestamp T-level valido",
            "     (signature-time-stamp o document-timestamp) che superi la validazione. -->",
        ],
    ),
]


def insert_before(text: str, anchor: str, comment_lines, occurrence: int = 1) -> str:
    lines = text.split("\n")
    count = 0
    for i, line in enumerate(lines):
        if anchor in line:
            count += 1
            if count == occurrence:
                indent = line[: len(line) - len(line.lstrip("\t"))]
                block = [indent + c for c in comment_lines]
                lines[i:i] = block
                return "\n".join(lines)
    raise AssertionError(f"anchor not found: {anchor}")


def annotate(xml: str, with_ts: bool) -> str:
    for anchor, comment in COMMENTS:
        if anchor not in xml:
            # TLevelTimeStamp only exists in the -ts variant; skip cleanly otherwise.
            if anchor == '<TLevelTimeStamp Level="FAIL" />' and not with_ts:
                continue
            raise AssertionError(f"anchor missing from generated policy: {anchor}")
        xml = insert_before(xml, anchor, comment)
    return header(with_ts) + xml


def make(with_ts: bool) -> str:
    xml = std
    name = (
        'Name="AGID-TS - Italian qualified/digital signature (QES, timestamped)"'
        if with_ts
        else 'Name="AGID - Italian qualified/digital signature (QES)"'
    )
    xml = xml.replace('Name="QES AES/QC AES TL based"', name, 1)

    xml = re.sub(
        r"<Description>.*?</Description>",
        "<Description>" + (DESC_TS if with_ts else DESC_AGID) + "</Description>",
        xml,
        count=1,
        flags=re.DOTALL,
    )

    # Trust service: require a CA/QC service that is (or was) granted. First match = main signature.
    xml, n = re.subn(
        r"<!--\s*<TrustServiceTypeIdentifier.*?</TrustServiceStatus>\s*-->",
        lambda m: TRUSTSERVICE_REPL,
        xml,
        count=1,
        flags=re.DOTALL,
    )
    assert n == 1, "TrustService comment block not found"

    xml = re.sub(
        r'<KeyUsage Level="WARN">(\s*<Id>nonRepudiation</Id>\s*)</KeyUsage>',
        lambda m: '<KeyUsage Level="FAIL">' + m.group(1) + "</KeyUsage>",
        xml,
        count=1,
    )
    xml = re.sub(
        r'<!--\s*<QcCompliance Level="WARN" />\s*-->',
        '\t\t\t\t<QcCompliance Level="FAIL" />',
        xml,
        count=1,
    )
    xml = re.sub(
        r'<!--\s*<QcSSCD Level="WARN" />\s*-->',
        '\t\t\t\t<QcSSCD Level="FAIL" />',
        xml,
        count=1,
    )
    xml = re.sub(
        r'<!--\s*<QcLegislationCountryCodes Level="WARN" />\s*-->',
        '\t\t\t\t<QcLegislationCountryCodes Level="WARN">\n'
        "\t\t\t\t\t<Id>IT</Id>\n"
        "\t\t\t\t</QcLegislationCountryCodes>",
        xml,
        count=1,
    )

    if with_ts:
        xml, n = re.subn(
            r"(<UnsignedAttributes>.*?)(\n\s*</UnsignedAttributes>)",
            lambda m: m.group(1) + '\n\t\t\t<TLevelTimeStamp Level="FAIL" />' + m.group(2),
            xml,
            count=1,
            flags=re.DOTALL,
        )
        assert n == 1, "UnsignedAttributes block not found"

    return annotate(xml, with_ts)


agid = make(False)
agid_ts = make(True)

# Sanity assertions before writing.
for x in (agid, agid_ts):
    assert '<KeyUsage Level="FAIL">\n\t\t\t\t\t<Id>nonRepudiation</Id>' in x, "nonRep FAIL missing"
    assert '<QcCompliance Level="FAIL" />' in x, "QcCompliance missing"
    assert '<QcSSCD Level="FAIL" />' in x, "QcSSCD FAIL missing"
    assert '<TrustServiceTypeIdentifier Level="FAIL">' in x
    assert x.count('<TrustServiceTypeIdentifier Level="FAIL">') == 1
    assert "<Id>IT</Id>" in x
    assert "PROFILO AGID" in x  # header present
    assert "<ExtendedKeyUsage Level=\"FAIL\">" in x and "<Id>timeStamping</Id>" in x
assert '<TLevelTimeStamp Level="FAIL" />' not in agid
assert '<TLevelTimeStamp Level="FAIL" />' in agid_ts
assert agid.count('<KeyUsage Level="WARN">') == 1  # countersig nonRep stays WARN

(base / "AGID.xml").write_text(agid)
(base / "AGID_TS.xml").write_text(agid_ts)
print("WROTE AGID.xml AGID_TS.xml")
print("AGID lines:", agid.count("\n") + 1, "| AGID_TS lines:", agid_ts.count("\n") + 1)
