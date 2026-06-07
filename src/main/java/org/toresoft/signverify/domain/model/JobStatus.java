package org.toresoft.signverify.domain.model;

public enum JobStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED,
  DELIVERED,
  DELIVERY_FAILED,
  DELETED;

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == DELIVERED || this == DELIVERY_FAILED;
  }
}
