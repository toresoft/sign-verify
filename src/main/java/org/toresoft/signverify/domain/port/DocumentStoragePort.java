package org.toresoft.signverify.domain.port;

public interface DocumentStoragePort {
  String storeInput(String jobId, String filename, byte[] content);

  String storeResult(String jobId, byte[] content);

  byte[] read(String path);

  void delete(String path);
}
