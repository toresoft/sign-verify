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
package org.toresoft.signverify.application;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class PolicyOverrideApplier {

  private static final Map<String, List<String>> CHECK_TO_TAGS =
      Map.of(
          "checkRevocation",
              List.of(
                  "RevocationDataAvailable", "RevocationDataFreshness", "RevocationCertHashMatch"),
          "checkSignatureIntegrity", List.of("SignatureIntact", "SignatureValid"),
          "checkCertificateChain", List.of("ProspectiveCertificateChain", "TrustedServiceStatus"),
          "checkTimestamp", List.of("TimestampDelay", "MessageImprintDataIntact"),
          "checkQualified", List.of("QualifiedCertificate"));

  public String apply(String xml, Map<String, Object> overrides) {
    if (overrides == null || overrides.isEmpty()) return xml;
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      var builder = factory.newDocumentBuilder();
      var doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));

      for (var entry : overrides.entrySet()) {
        if (entry.getValue() instanceof Boolean b && !b) {
          for (String tag : CHECK_TO_TAGS.getOrDefault(entry.getKey(), List.of())) {
            setLevelOnAll(doc, tag, "IGNORE");
          }
        }
      }
      return toString(doc);
    } catch (Exception e) {
      throw new IllegalArgumentException("override application failed", e);
    }
  }

  private void setLevelOnAll(org.w3c.dom.Document doc, String localName, String level) {
    NodeList nodes = doc.getElementsByTagNameNS("*", localName);
    for (int i = 0; i < nodes.getLength(); i++) {
      ((Element) nodes.item(i)).setAttribute("Level", level);
    }
  }

  private String toString(org.w3c.dom.Document doc) throws Exception {
    var tf = TransformerFactory.newInstance();
    var t = tf.newTransformer();
    t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    StringWriter sw = new StringWriter();
    t.transform(new DOMSource(doc), new StreamResult(sw));
    return sw.toString();
  }
}
