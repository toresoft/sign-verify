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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.toresoft.signverify.domain.model.TrustedCertificate;

public interface TrustedCertificateRepository
    extends JpaRepository<TrustedCertificate, UUID>, JpaSpecificationExecutor<TrustedCertificate> {

  Optional<TrustedCertificate> findByFingerprintSha256(String fp);

  @Query("select c.fingerprintSha256 from TrustedCertificate c where c.removedAt is null")
  List<String> findActiveFingerprints();

  @Modifying
  @Query(
      "update TrustedCertificate c set c.lastSeenAt = :now"
          + " where c.removedAt is null and c.fingerprintSha256 in :fps")
  int touchLastSeen(@Param("now") Instant now, @Param("fps") Collection<String> fps);

  @Modifying
  @Query(
      "update TrustedCertificate c set c.removedAt = :now"
          + " where c.removedAt is null and c.fingerprintSha256 in :fps")
  int markRemoved(@Param("now") Instant now, @Param("fps") Collection<String> fps);
}
