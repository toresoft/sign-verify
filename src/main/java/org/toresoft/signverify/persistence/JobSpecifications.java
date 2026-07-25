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
package org.toresoft.signverify.persistence;

import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;

/**
 * Null-safe {@link Specification} factories for querying {@link ValidationJob}. A factory given a
 * null/empty argument returns a no-op specification (matches everything), so callers can compose
 * optional filters with {@code .and(...)} without null checks.
 */
public final class JobSpecifications {

  private JobSpecifications() {}

  private static Specification<ValidationJob> all() {
    return (root, q, cb) -> cb.conjunction();
  }

  public static Specification<ValidationJob> ownedBy(PrincipalType type, String id) {
    return ownerTypeEquals(type).and(ownerIdEquals(id));
  }

  public static Specification<ValidationJob> ownerIdEquals(String id) {
    return id == null ? all() : (root, q, cb) -> cb.equal(root.get("requestedByPrincipalId"), id);
  }

  public static Specification<ValidationJob> ownerTypeEquals(PrincipalType type) {
    return type == null
        ? all()
        : (root, q, cb) -> cb.equal(root.get("requestedByPrincipalType"), type);
  }

  public static Specification<ValidationJob> statusIn(Collection<JobStatus> statuses) {
    return statuses == null || statuses.isEmpty()
        ? all()
        : (root, q, cb) -> root.get("status").in(statuses);
  }

  public static Specification<ValidationJob> createdAtGte(Instant from) {
    return from == null
        ? all()
        : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
  }

  public static Specification<ValidationJob> createdAtLt(Instant to) {
    return to == null ? all() : (root, q, cb) -> cb.lessThan(root.get("createdAt"), to);
  }
}
