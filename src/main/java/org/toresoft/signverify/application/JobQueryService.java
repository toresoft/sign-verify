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

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.persistence.JobSpecifications;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

/** Lists verification jobs with role-based scoping, filtering, and pagination. */
@Service
public class JobQueryService {

  private static final int MAX_SIZE = 200;
  private static final int DEFAULT_SIZE = 50;
  private static final Sort CREATED_DESC = Sort.by(Sort.Direction.DESC, "createdAt");

  private final ValidationJobRepository repo;

  public JobQueryService(ValidationJobRepository repo) {
    this.repo = repo;
  }

  public JobPage list(Principal actor, JobQuery q) {
    boolean privileged = actor.role() == Role.PRIVILEGED;

    if (!privileged && (q.ownerId() != null || q.ownerType() != null)) {
      throw AppException.badRequest("owner filter requires PRIVILEGED");
    }
    if (q.createdOn() != null && (q.createdFrom() != null || q.createdTo() != null)) {
      throw AppException.badRequest("createdOn cannot be combined with createdFrom/createdTo");
    }
    if (q.createdFrom() != null
        && q.createdTo() != null
        && q.createdFrom().isAfter(q.createdTo())) {
      throw AppException.badRequest("createdFrom must not be after createdTo");
    }

    Instant from;
    Instant to;
    if (q.createdOn() != null) {
      from = q.createdOn().atStartOfDay(ZoneOffset.UTC).toInstant();
      to = q.createdOn().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    } else {
      from = q.createdFrom();
      to = q.createdTo();
    }

    Specification<ValidationJob> scope =
        privileged
            ? JobSpecifications.ownerIdEquals(q.ownerId())
                .and(JobSpecifications.ownerTypeEquals(q.ownerType()))
            : JobSpecifications.ownedBy(actor.type(), actor.id());

    Specification<ValidationJob> spec =
        scope
            .and(JobSpecifications.statusIn(q.statuses()))
            .and(JobSpecifications.createdAtGte(from))
            .and(JobSpecifications.createdAtLt(to));

    int page = Math.max(0, q.page());
    int size = q.size() <= 0 ? DEFAULT_SIZE : Math.min(MAX_SIZE, q.size());

    Page<ValidationJob> result = repo.findAll(spec, PageRequest.of(page, size, CREATED_DESC));
    List<JobSummaryDto> content = result.getContent().stream().map(JobSummaryDto::from).toList();
    return new JobPage(
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages(),
        content);
  }
}
