package org.toresoft.signverify.adapter.dss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.junit.jupiter.api.Test;

/**
 * Probes how DSS 6.4 handles RFC 5544 TimeStampedData (TSD) format.
 *
 * <p>RFC 5544 TSD wraps a document + RFC 3161 timestamp token in a CMS ContentInfo with contentType
 * = id-aa-timeStampedData (OID 1.2.840.113549.1.9.16.1.31). This is structurally different from
 * CAdES (id-signedData). The test builds a minimal syntactically-partial TSD (no real TSA token
 * needed) to probe the DSS routing layer.
 *
 * <p>Expected findings — one of:
 *
 * <pre>
 *   A) fromDocument() throws UnsupportedOperationException("Document format not recognized")
 *      → DSS has NO factory for RFC 5544 TSD; sign-verify-2 needs custom parsing.
 *
 *   B) fromDocument() succeeds, routes to CMSDocumentValidator, then validateDocument()
 *      fails/returns 0 signatures
 *      → DSS misidentifies TSD as CAdES but then fails; still needs custom handling.
 *
 *   C) fromDocument() succeeds and validateDocument() returns 0-sig report
 *      → DSS silently ignores the TSD wrapper; inner .p7m would need separate extraction.
 *
 *   D) fromDocument() succeeds and DSS routes to a TSD-specific validator
 *      → DSS has hidden TSD support; existing DssValidatorAdapter may already work.
 * </pre>
 */
class Rfc5544TsdRoutingTest {

  // id-aa-timeStampedData OID defined in RFC 5544 §2
  private static final ASN1ObjectIdentifier TSD_OID =
      new ASN1ObjectIdentifier("1.2.840.113549.1.9.16.1.31");

  /**
   * Builds a minimal RFC 5544 ContentInfo. Structure: ContentInfo { contentType:
   * id-aa-timeStampedData, content: TimeStampedData { version: 1, content: <bytes> } } Note:
   * temporalEvidence (mandatory per RFC) is omitted — we only care about routing, not full
   * validation.
   */
  private static byte[] buildMinimalTsd(byte[] innerContent) throws Exception {
    ASN1EncodableVector tsdFields = new ASN1EncodableVector();
    tsdFields.add(new ASN1Integer(1)); // version v1
    tsdFields.add(new DEROctetString(innerContent)); // content OCTET STRING
    // temporalEvidence omitted (invalid per RFC but sufficient to test DSS routing)
    DERSequence tsdSeq = new DERSequence(tsdFields);
    ContentInfo ci = new ContentInfo(TSD_OID, tsdSeq);
    return ci.getEncoded("DER");
  }

  @Test
  void rfc5544_tsd_is_not_detected_as_timestamp_token() throws Exception {
    byte[] tsdBytes = buildMinimalTsd("Hello, TSD!".getBytes());
    InMemoryDocument doc = new InMemoryDocument(tsdBytes, "test.tsd");

    // DSSUtils.isTimestampToken checks encapContentInfo.eContentType == id-ct-TSTInfo.
    // For RFC 5544 TSD, the outer ContentInfo.contentType = id-aa-timeStampedData (not
    // id-signedData), so CMSSignedDataParser fails → exception swallowed → returns false.
    boolean isPureTs = DSSUtils.isTimestampToken(doc);

    assertThat(isPureTs)
        .as(
            "RFC 5544 TSD should NOT be detected as a pure timestamp token (id-ct-TSTInfo);"
                + " it has contentType id-aa-timeStampedData")
        .isFalse();
  }

  @Test
  void rfc5544_tsd_routing_via_from_document() throws Exception {
    byte[] tsdBytes = buildMinimalTsd("Hello, TSD!".getBytes());
    InMemoryDocument doc = new InMemoryDocument(tsdBytes, "test.tsd");

    // This is the key probe: does DSS recognise the RFC 5544 ContentInfo at all?
    // See Javadoc on this class for the 4 possible outcomes (A/B/C/D).
    var validatorOrNull =
        new Object() {
          SignedDocumentValidator value;
          String validatorClass;
          String thrownMessage;
        };

    try {
      validatorOrNull.value = SignedDocumentValidator.fromDocument(doc);
      validatorOrNull.validatorClass = validatorOrNull.value.getClass().getName();
      System.out.println("[TSD-PROBE] fromDocument() OK → " + validatorOrNull.validatorClass);
    } catch (Exception e) {
      validatorOrNull.thrownMessage = e.getClass().getName() + ": " + e.getMessage();
      System.out.println("[TSD-PROBE] fromDocument() threw → " + validatorOrNull.thrownMessage);
    }

    // If fromDocument() succeeded, probe validateDocument() as well.
    if (validatorOrNull.value != null) {
      assertThatCode(
              () -> {
                var reports = validatorOrNull.value.validateDocument();
                var sr = reports.getSimpleReport();
                System.out.println(
                    "[TSD-PROBE] validateDocument() OK: signaturesCount="
                        + sr.getSignaturesCount()
                        + " timestampIds="
                        + sr.getTimestampIdList());
              })
          .as("validateDocument() on RFC 5544 TSD (outcome B/C/D)")
          .doesNotThrowAnyException();
    } else {
      // Outcome A: fromDocument() itself threw — DSS has no factory for RFC 5544 TSD.
      assertThat(validatorOrNull.thrownMessage)
          .as("Outcome A: UnsupportedOperationException expected when no factory matches")
          .isNotNull();
    }
  }
}
