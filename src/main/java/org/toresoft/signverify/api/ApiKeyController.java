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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.toresoft.signverify.api.dto.*;
import org.toresoft.signverify.api.spi.ApiKeysApi;
import org.toresoft.signverify.application.ApiKeyService;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.security.Principal;

@RestController
@PreAuthorize("hasRole('PRIVILEGED')")
public class ApiKeyController implements ApiKeysApi {

  private final ApiKeyService service;

  public ApiKeyController(ApiKeyService service) {
    this.service = service;
  }

  @Override
  public ResponseEntity<ApiKeyPage> listApiKeys(Integer page, Integer size) {
    var p = service.findAll(PageRequest.of(page == null ? 0 : page, size == null ? 20 : size));
    ApiKeyPage out = new ApiKeyPage();
    out.setPage(p.getNumber());
    out.setSize(p.getSize());
    out.setTotalElements(p.getTotalElements());
    out.setTotalPages(p.getTotalPages());
    out.setContent(p.map(this::toView).toList());
    return ResponseEntity.ok(out);
  }

  @Override
  public ResponseEntity<ApiKeyCreatedResponse> createApiKey(ApiKeyCreateRequest req) {
    Principal actor = currentPrincipal();
    var expAt = req.getExpiresAt();
    var res =
        service.create(
            req.getName(),
            Role.valueOf(req.getRole().getValue()),
            expAt != null && expAt.isPresent() ? expAt.get().toInstant() : null,
            actor);
    ApiKeyCreatedResponse out = new ApiKeyCreatedResponse();
    fillCreatedView(out, res.entity());
    out.setPlaintextKey(res.plaintext());
    return ResponseEntity.status(201).body(out);
  }

  @Override
  public ResponseEntity<Void> deleteApiKey(UUID id) {
    service.delete(id, currentPrincipal());
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<ApiKeyView> patchApiKey(UUID id, ApiKeyPatchRequest req) {
    ApiKey k = service.patch(id, req.getEnabled(), currentPrincipal());
    return ResponseEntity.ok(toView(k));
  }

  private Principal currentPrincipal() {
    var auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    return (Principal) auth.getPrincipal();
  }

  private ApiKeyView toView(ApiKey k) {
    ApiKeyView v = new ApiKeyView();
    fillView(v, k);
    return v;
  }

  private void fillView(ApiKeyView v, ApiKey k) {
    v.setId(k.getId());
    v.setName(k.getName());
    v.setKeyPrefix(k.getKeyPrefix());
    v.setRole(ApiKeyView.RoleEnum.valueOf(k.getRole().name()));
    v.setEnabled(k.isEnabled());
    v.setBootstrap(k.isBootstrap());
    v.setCreatedAt(k.getCreatedAt().atOffset(ZoneOffset.UTC));
    v.setExpiresAt(
        k.getExpiresAt() == null
            ? JsonNullable.<OffsetDateTime>undefined()
            : JsonNullable.of(k.getExpiresAt().atOffset(ZoneOffset.UTC)));
    v.setLastUsedAt(
        k.getLastUsedAt() == null
            ? JsonNullable.<OffsetDateTime>undefined()
            : JsonNullable.of(k.getLastUsedAt().atOffset(ZoneOffset.UTC)));
  }

  private void fillCreatedView(ApiKeyCreatedResponse v, ApiKey k) {
    v.setId(k.getId());
    v.setName(k.getName());
    v.setKeyPrefix(k.getKeyPrefix());
    v.setRole(ApiKeyCreatedResponse.RoleEnum.valueOf(k.getRole().name()));
    v.setEnabled(k.isEnabled());
    v.setBootstrap(k.isBootstrap());
    v.setCreatedAt(k.getCreatedAt().atOffset(ZoneOffset.UTC));
    v.setExpiresAt(
        k.getExpiresAt() == null
            ? JsonNullable.<OffsetDateTime>undefined()
            : JsonNullable.of(k.getExpiresAt().atOffset(ZoneOffset.UTC)));
    v.setLastUsedAt(
        k.getLastUsedAt() == null
            ? JsonNullable.<OffsetDateTime>undefined()
            : JsonNullable.of(k.getLastUsedAt().atOffset(ZoneOffset.UTC)));
  }
}
