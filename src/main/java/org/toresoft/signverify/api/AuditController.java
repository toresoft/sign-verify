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
package org.toresoft.signverify.api;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.persistence.AuditLogRepository;

@RestController
@RequestMapping("/api/v1/audit-log")
@PreAuthorize("hasRole('PRIVILEGED')")
public class AuditController {

  private final AuditLogRepository repo;

  public AuditController(AuditLogRepository repo) {
    this.repo = repo;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String principalId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) String targetId,
      @RequestParam(required = false) Boolean success,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    Specification<AuditLog> spec =
        (root, q, cb) -> {
          List<Predicate> ps = new ArrayList<>();
          if (principalId != null) ps.add(cb.equal(root.get("principalId"), principalId));
          if (action != null) ps.add(cb.equal(root.get("action"), action));
          if (from != null)
            ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.toInstant()));
          if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to.toInstant()));
          if (targetType != null) ps.add(cb.equal(root.get("targetType"), targetType));
          if (targetId != null) ps.add(cb.equal(root.get("targetId"), targetId));
          if (success != null) ps.add(cb.equal(root.get("success"), success));
          return cb.and(ps.toArray(new Predicate[0]));
        };
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
    var p = repo.findAll(spec, pageable);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("page", pageable.getPageNumber());
    result.put("size", pageable.getPageSize());
    result.put("totalElements", p.getTotalElements());
    result.put("totalPages", p.getTotalPages());
    result.put("content", p.getContent());
    return result;
  }
}
