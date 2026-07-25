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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.toresoft.signverify.application.JobPage;
import org.toresoft.signverify.application.JobQuery;
import org.toresoft.signverify.application.JobQueryService;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.security.Principal;

/**
 * Lists verification jobs. Scoping (own vs all) and validation live in {@link JobQueryService};
 * this layer only binds query params and returns the envelope.
 */
@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationJobListController {

  private final JobQueryService service;

  public VerificationJobListController(JobQueryService service) {
    this.service = service;
  }

  @GetMapping("/jobs")
  public JobPage list(
      @RequestParam(required = false) String ownerId,
      @RequestParam(required = false) PrincipalType ownerType,
      @RequestParam(required = false) List<JobStatus> status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate createdOn,
      @RequestParam(required = false) OffsetDateTime createdFrom,
      @RequestParam(required = false) OffsetDateTime createdTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    Principal actor =
        (Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    JobQuery query =
        new JobQuery(
            ownerId,
            ownerType,
            status,
            createdOn,
            createdFrom == null ? null : createdFrom.toInstant(),
            createdTo == null ? null : createdTo.toInstant(),
            page,
            size);

    return service.list(actor, query);
  }
}
