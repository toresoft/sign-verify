package org.toresoft.signverify.adapter.dss;

/**
 * Magic-byte content sniffing used to deduce a filename/mime when the caller omits the multipart
 * filename, and to name TSD/CAdES leaf content that carries no embedded name.
 *
 * <p>Best-effort heuristics only: the authoritative container decision is made by the DSS extractor
 * during recursion. A DER SEQUENCE is mapped to {@code .p7m} as the reasonable default for binary
 * ASN.1 in this domain.
 */
public final class ContentTypeDetector {

  public record DetectedType(String mimeType, String extension) {}

  private static final DetectedType PDF = new DetectedType("application/pdf", ".pdf");
  private static final DetectedType ZIP = new DetectedType("application/zip", ".zip");
  private static final DetectedType XML = new DetectedType("application/xml", ".xml");
  private static final DetectedType P7M = new DetectedType("application/pkcs7-mime", ".p7m");
  private static final DetectedType DEFAULT = new DetectedType("application/octet-stream", ".bin");

  private ContentTypeDetector() {}

  public static DetectedType detect(byte[] content) {
    if (content == null || content.length < 2) {
      return DEFAULT;
    }
    if (startsWith(content, new byte[] {0x25, 0x50, 0x44, 0x46})) { // %PDF
      return PDF;
    }
    if (startsWith(content, new byte[] {0x50, 0x4B, 0x03, 0x04})) { // PK\x03\x04
      return ZIP;
    }
    if (firstNonWhitespaceIsAngleBracket(content)) {
      return XML;
    }
    // DER SEQUENCE tag 0x30 with long-form length (0x81/0x82/0x83) — typical CMS/CAdES envelope.
    if (content[0] == 0x30) {
      int lenByte = content[1] & 0xFF;
      if (lenByte == 0x81 || lenByte == 0x82 || lenByte == 0x83) {
        return P7M;
      }
    }
    return DEFAULT;
  }

  public static String syntheticName(String stem, byte[] content) {
    return stem + detect(content).extension();
  }

  private static boolean startsWith(byte[] content, byte[] prefix) {
    if (content.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (content[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static boolean firstNonWhitespaceIsAngleBracket(byte[] content) {
    for (byte b : content) {
      if (b == ' ' || b == '\t' || b == '\r' || b == '\n' || b == (byte) 0xEF /* BOM */) {
        continue;
      }
      return b == '<';
    }
    return false;
  }
}
