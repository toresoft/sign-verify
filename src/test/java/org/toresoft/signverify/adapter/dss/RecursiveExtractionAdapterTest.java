package org.toresoft.signverify.adapter.dss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.junit.jupiter.api.Test;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ExtractionPort;

class RecursiveExtractionAdapterTest {

  private static final ASN1ObjectIdentifier TSD_OID =
      new ASN1ObjectIdentifier("1.2.840.113549.1.9.16.1.31");

  // Placeholder contentType for the fake inner "timestamp token" ContentInfo below. BC's
  // TimeStampAndCRL only parses this generically (ContentInfo.getInstance) without validating
  // that it is a real RFC 3161 token, so any OID works.
  private static final ASN1ObjectIdentifier DUMMY_TIMESTAMP_OID =
      new ASN1ObjectIdentifier("1.2.3.4.5");

  /**
   * Minimal RFC 5544 TSD wrapping arbitrary inner bytes (routing-only, no real TSA signature).
   *
   * <p>BC's {@code CMSTimeStampedData} requires a syntactically valid {@code temporalEvidence} (the
   * RFC 5544 {@code Evidence} CHOICE) even though its content is never cryptographically validated
   * by a plain unwrap: the constructor unconditionally calls {@code
   * evidence.getTstEvidence().toTimeStampAndCRLArray()}. A {@code tstEvidence} is a SEQUENCE of
   * {@code TimeStampAndCRL}, each just wrapping a {@code ContentInfo} (parsed generically, no
   * content-type check) — so a fake-but-well-formed one is enough to satisfy BC's parser.
   */
  private static byte[] tsd(byte[] inner) throws Exception {
    ASN1EncodableVector fakeTimestampToken = new ASN1EncodableVector();
    fakeTimestampToken.add(DUMMY_TIMESTAMP_OID);

    ASN1EncodableVector timeStampAndCrl = new ASN1EncodableVector();
    timeStampAndCrl.add(new DERSequence(fakeTimestampToken));

    ASN1EncodableVector tstEvidence = new ASN1EncodableVector();
    tstEvidence.add(new DERSequence(timeStampAndCrl));
    DERTaggedObject temporalEvidence = new DERTaggedObject(false, 0, new DERSequence(tstEvidence));

    ASN1EncodableVector v = new ASN1EncodableVector();
    v.add(new ASN1Integer(1));
    v.add(new DEROctetString(inner));
    v.add(temporalEvidence);
    return new ContentInfo(TSD_OID, new DERSequence(v)).getEncoded("DER");
  }

  /**
   * Stub single-level delegate: returns a preconfigured result on its first call, then throws a
   * parse error on every subsequent call — simulating a container whose extracted originals are
   * themselves plain (non-container) leaves, without looping forever.
   */
  private static final class StubDelegate extends DssExtractionAdapter {
    private final ExtractionResult result;
    private boolean consumed;

    StubDelegate(ExtractionResult result) {
      super(null);
      this.result = result;
    }

    @Override
    public ExtractionResult extract(byte[] bytes, String filename) {
      if (result == null || consumed) {
        throw AppException.signatureParseError("no signatures found");
      }
      consumed = true;
      return result;
    }
  }

  @Test
  void single_tsd_wrapping_a_leaf_returns_raw_inner_with_deduced_name() throws Exception {
    byte[] pdfLeaf = "%PDF-1.7 body".getBytes();
    byte[] outer = tsd(pdfLeaf);
    var adapter = new RecursiveExtractionAdapter(new StubDelegate(null));

    var result = adapter.extract(outer, "sample.tsd");

    assertThat(result.signatureFormat()).isEqualTo(RecursiveExtractionAdapter.TSD_FORMAT);
    assertThat(result.originals()).hasSize(1);
    ExtractionPort.ExtractedFile leaf = result.originals().get(0);
    assertThat(leaf.content()).isEqualTo(pdfLeaf);
    assertThat(leaf.mimeType()).isEqualTo("application/pdf");
    assertThat(leaf.filename()).endsWith(".pdf");
  }

  @Test
  void nested_tsd_in_tsd_unwraps_to_the_innermost_leaf() throws Exception {
    byte[] leaf = "%PDF-inner".getBytes();
    byte[] nested = tsd(tsd(leaf));
    var adapter = new RecursiveExtractionAdapter(new StubDelegate(null));

    var result = adapter.extract(nested, "x.tsd");

    assertThat(result.originals()).hasSize(1);
    assertThat(result.originals().get(0).content()).isEqualTo(leaf);
  }

  @Test
  void tsd_wrapping_a_dss_container_recurses_into_delegate_originals() throws Exception {
    byte[] realLeaf = "%PDF-final".getBytes();
    // First delegate call (on the p7m inner) yields one original that is itself a plain leaf.
    var delegate =
        new StubDelegate(
            new ExtractionPort.ExtractionResult(
                "CAdES",
                List.of(
                    new ExtractionPort.ExtractedFile("payload.pdf", "application/pdf", realLeaf))));
    // Inner of the TSD is a DER SEQUENCE so tryUnwrapTsd fails and the delegate is used.
    byte[] p7mLike = {0x30, (byte) 0x82, 0x01, 0x00};
    byte[] outer = tsd(p7mLike);
    var adapter = new RecursiveExtractionAdapter(delegate);

    var result = adapter.extract(outer, "c.tsd");

    // Outermost container is the TSD, even though the delegate's own format (CAdES) sits
    // one level down.
    assertThat(result.signatureFormat()).isEqualTo(RecursiveExtractionAdapter.TSD_FORMAT);
    // realLeaf is returned as-is: the stub delegate's canned CAdES result is consumed once (for
    // the p7m-like inner), then throws parse-error on the recursive call over its own original
    // (realLeaf), which — at depth > 0 — becomes a raw leaf instead of propagating.
    assertThat(result.originals()).hasSize(1);
    assertThat(result.originals().get(0).content()).isEqualTo(realLeaf);
  }

  @Test
  void top_level_non_container_propagates_parse_error() {
    // depth == 0, not a TSD, delegate throws -> error must propagate (bad user input).
    byte[] plain = "not a signed doc".getBytes();
    var adapter = new RecursiveExtractionAdapter(new StubDelegate(null));

    assertThatThrownBy(() -> adapter.extract(plain, "plain.txt")).isInstanceOf(AppException.class);
  }

  @Test
  void nesting_beyond_max_depth_throws_app_exception() throws Exception {
    byte[] deeplyNested = "%PDF-leaf".getBytes();
    for (int i = 0; i < 12; i++) {
      deeplyNested = tsd(deeplyNested);
    }
    var adapter = new RecursiveExtractionAdapter(new StubDelegate(null));
    byte[] finalDeeplyNested = deeplyNested;

    assertThatThrownBy(() -> adapter.extract(finalDeeplyNested, "deep.tsd"))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("max depth");
  }
}
