/**
 * sign-verify Copyright (C) 2026 toresoft
 *
 * <p>This file is part of the "sign-verify" project.
 *
 * <p>This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301 USA
 */
package org.toresoft.signverify.adapter.dss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import org.toresoft.signverify.config.ExtractionProperties;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ExtractionPort.ExtractedFile;
import org.toresoft.signverify.domain.port.ExtractionPort.ExtractionResult;

class RecursiveExtractionAdapterTest {

  private static final ASN1ObjectIdentifier TSD_OID =
      new ASN1ObjectIdentifier("1.2.840.113549.1.9.16.1.31");

  // Placeholder contentType for the fake inner "timestamp token" ContentInfo below. BC's
  // TimeStampAndCRL only parses this generically (ContentInfo.getInstance) without validating
  // that it is a real RFC 3161 token, so any OID works.
  private static final ASN1ObjectIdentifier DUMMY_TIMESTAMP_OID =
      new ASN1ObjectIdentifier("1.2.3.4.5");

  private static ExtractionProperties props(Path base) {
    var p = new ExtractionProperties();
    p.setTempDir(base);
    p.setMaxLeaves(1000);
    p.setMaxLeafBytes(DataSize.ofMegabytes(10));
    p.setMaxTotalBytes(DataSize.ofMegabytes(50));
    return p;
  }

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
   * Stub single-level delegate: returns a preconfigured parse on its first call, then throws a
   * parse error on every subsequent call — simulating a container whose extracted originals are
   * themselves plain (non-container) leaves, without looping forever.
   */
  private static final class StubDelegate extends DssExtractionAdapter {
    private final DssParse result;
    private boolean consumed;

    StubDelegate(DssParse result) {
      super(null);
      this.result = result;
    }

    @Override
    public DssParse parse(DSSDocument doc) {
      if (result == null || consumed) {
        throw AppException.signatureParseError("no signatures found");
      }
      consumed = true;
      return result;
    }
  }

  @Test
  void single_tsd_wrapping_a_leaf_returns_raw_inner_with_deduced_name(@TempDir Path base)
      throws Exception {
    byte[] pdfLeaf = "%PDF-1.7 body".getBytes();
    byte[] outer = tsd(pdfLeaf);
    var adapter = new RecursiveExtractionAdapter(new StubDelegate(null), props(base));

    try (ExtractionResult result = adapter.extract(outer, "sample.tsd")) {
      assertThat(result.signatureFormat()).isEqualTo(RecursiveExtractionAdapter.TSD_FORMAT);
      assertThat(result.originals()).hasSize(1);
      ExtractedFile leaf = result.originals().get(0);
      assertThat(Files.readAllBytes(leaf.path())).isEqualTo(pdfLeaf);
      assertThat(leaf.mimeType()).isEqualTo("application/pdf");
      assertThat(leaf.filename()).endsWith(".pdf");
      assertThat(leaf.size()).isEqualTo(pdfLeaf.length);
    }
  }

  @Test
  void nested_tsd_in_tsd_unwraps_to_the_innermost_leaf(@TempDir Path base) throws Exception {
    byte[] leaf = "%PDF-inner".getBytes();
    byte[] nested = tsd(tsd(leaf));
    var adapter = new RecursiveExtractionAdapter(new StubDelegate(null), props(base));

    try (ExtractionResult result = adapter.extract(nested, "x.tsd")) {
      assertThat(result.originals()).hasSize(1);
      assertThat(Files.readAllBytes(result.originals().get(0).path())).isEqualTo(leaf);
    }
  }

  @Test
  void tsd_wrapping_a_dss_container_recurses_into_delegate_originals(@TempDir Path base)
      throws Exception {
    byte[] realLeaf = "%PDF-final".getBytes();
    // First delegate call (on the p7m inner) yields one original that is itself a plain leaf.
    var delegate =
        new StubDelegate(
            new DssExtractionAdapter.DssParse(
                "CAdES", List.of(new InMemoryDocument(realLeaf, "payload.pdf"))));
    // Inner of the TSD is a DER SEQUENCE so tryUnwrapTsd fails and the delegate is used.
    byte[] p7mLike = {0x30, (byte) 0x82, 0x01, 0x00};
    byte[] outer = tsd(p7mLike);
    var adapter = new RecursiveExtractionAdapter(delegate, props(base));

    try (ExtractionResult result = adapter.extract(outer, "c.tsd")) {
      // Outermost container is the TSD, even though the delegate's own format (CAdES) sits
      // one level down.
      assertThat(result.signatureFormat()).isEqualTo(RecursiveExtractionAdapter.TSD_FORMAT);
      // realLeaf is returned as-is: the stub delegate's canned CAdES parse is consumed once (for
      // the p7m-like inner), then throws parse-error on the recursive call over its own original
      // (realLeaf), which — at depth > 0 — becomes a raw leaf instead of propagating.
      assertThat(result.originals()).hasSize(1);
      assertThat(Files.readAllBytes(result.originals().get(0).path())).isEqualTo(realLeaf);
    }
  }

  @Test
  void top_level_non_container_propagates_parse_error(@TempDir Path base) {
    // depth == 0, not a TSD, delegate throws -> error must propagate (bad user input).
    byte[] plain = "not a signed doc".getBytes();
    var adapter = new RecursiveExtractionAdapter(new StubDelegate(null), props(base));

    assertThatThrownBy(() -> adapter.extract(plain, "plain.txt")).isInstanceOf(AppException.class);
  }

  @Test
  void nesting_beyond_max_depth_throws_app_exception(@TempDir Path base) throws Exception {
    byte[] deeplyNested = "%PDF-leaf".getBytes();
    for (int i = 0; i < 12; i++) {
      deeplyNested = tsd(deeplyNested);
    }
    var adapter = new RecursiveExtractionAdapter(new StubDelegate(null), props(base));
    byte[] finalDeeplyNested = deeplyNested;

    // AppException.getMessage() returns the RFC 9457 title ("Bad Request"); the descriptive
    // text lives in getDetail(), so assert on the thrown instance's detail.
    AppException thrown =
        catchThrowableOfType(
            () -> adapter.extract(finalDeeplyNested, "deep.tsd"), AppException.class);
    assertThat(thrown).isNotNull();
    assertThat(thrown.getDetail()).contains("max depth");
  }

  @Test
  void max_leaves_cap_is_enforced(@TempDir Path base) throws Exception {
    byte[] leafA = "AAAA".getBytes();
    byte[] leafB = "BBBB".getBytes();
    var delegate =
        new StubDelegate(
            new DssExtractionAdapter.DssParse(
                "ASiC-E",
                List.of(
                    new InMemoryDocument(leafA, "a.txt"), new InMemoryDocument(leafB, "b.txt"))));
    byte[] container = {0x30, (byte) 0x82, 0x01, 0x00};
    var p = props(base);
    p.setMaxLeaves(1);
    var adapter = new RecursiveExtractionAdapter(delegate, p);

    assertThatThrownBy(() -> adapter.extract(container, "c.p7m")).isInstanceOf(AppException.class);
  }
}
