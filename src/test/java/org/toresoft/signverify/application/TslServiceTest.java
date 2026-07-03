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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import eu.europa.esig.dss.model.tsl.DownloadInfoRecord;
import eu.europa.esig.dss.model.tsl.LOTLInfo;
import eu.europa.esig.dss.model.tsl.TLInfo;
import eu.europa.esig.dss.model.tsl.TLValidationJobSummary;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.toresoft.signverify.domain.model.RefreshStatus;
import org.toresoft.signverify.domain.model.RefreshTrigger;
import org.toresoft.signverify.domain.model.TslRefresh;
import org.toresoft.signverify.domain.port.TrustedListMirrorPort;
import org.toresoft.signverify.persistence.TrustedCertificateRepository;
import org.toresoft.signverify.persistence.TslRefreshRepository;

/** Unit tests for {@link TslService#refresh}: how a refresh run is recorded. */
@ExtendWith(MockitoExtension.class)
class TslServiceTest {

  @Mock private TLValidationJob job;
  @Mock private TrustedListMirrorPort mirror;
  @Mock private TrustedCertificateRepository certRepo;
  @Mock private TslRefreshRepository refreshRepo;

  private TslService service;

  @BeforeEach
  void setUp() {
    service = new TslService(job, mirror, certRepo, refreshRepo);
  }

  @Test
  void refresh_success_recordsDiffAndMarksReady() {
    when(mirror.sync()).thenReturn(new TrustedListMirrorPort.Diff(10, 2, 300));

    TslRefresh r = service.refresh(RefreshTrigger.MANUAL, null);

    assertThat(r.getStatus()).isEqualTo(RefreshStatus.SUCCESS);
    assertThat(r.getCertificatesAdded()).isEqualTo(10);
    assertThat(r.getCertificatesRemoved()).isEqualTo(2);
    assertThat(r.getCertificatesUnchanged()).isEqualTo(300);
    assertThat(r.getCompletedAt()).isNotNull();
    assertThat(r.getTrigger()).isEqualTo(RefreshTrigger.MANUAL);
    assertThat(service.isReady()).isTrue();
  }

  @Test
  void refresh_onlineRefreshFailure_recordsFailed() {
    doThrow(new RuntimeException("network down")).when(job).onlineRefresh();

    TslRefresh r = service.refresh(RefreshTrigger.SCHEDULED, null);

    assertThat(r.getStatus()).isEqualTo(RefreshStatus.FAILED);
    assertThat(r.getErrorSummary()).contains("network down");
    assertThat(r.getCompletedAt()).isNotNull();
    assertThat(service.isReady()).isFalse();
  }

  @Test
  void refresh_mirrorFailure_recordsFailed() {
    when(mirror.sync()).thenThrow(new IllegalStateException("db error"));

    TslRefresh r = service.refresh(RefreshTrigger.MANUAL, null);

    assertThat(r.getStatus()).isEqualTo(RefreshStatus.FAILED);
    assertThat(r.getErrorSummary()).contains("db error");
  }

  // --- per-source counters from the DSS job summary ---------------------------

  private TLInfo tlInfo(boolean failed) {
    TLInfo tl = mock(TLInfo.class);
    DownloadInfoRecord d = mock(DownloadInfoRecord.class);
    lenient().when(d.isError()).thenReturn(failed);
    lenient().when(tl.getDownloadCacheInfo()).thenReturn(d);
    return tl;
  }

  private LOTLInfo lotlInfo(List<TLInfo> tls) {
    LOTLInfo lotl = mock(LOTLInfo.class);
    lenient().when(lotl.getTLInfos()).thenReturn(tls);
    return lotl;
  }

  @Test
  void refresh_partialSourceFailure_recordsCountersAndPartialStatus() {
    when(mirror.sync()).thenReturn(new TrustedListMirrorPort.Diff(1, 0, 10));
    LOTLInfo lotl = lotlInfo(List.of(tlInfo(false), tlInfo(true)));
    TLValidationJobSummary summary = mock(TLValidationJobSummary.class);
    when(summary.getLOTLInfos()).thenReturn(List.of(lotl));
    when(summary.getOtherTLInfos()).thenReturn(List.of());
    when(job.getSummary()).thenReturn(summary);

    TslRefresh r = service.refresh(RefreshTrigger.MANUAL, null);

    // 1 LOTL + 2 TLs = 3 sources, of which 1 failed → PARTIAL, but still ready (some trust
    // anchors were loaded).
    assertThat(r.getSourcesTotal()).isEqualTo(3);
    assertThat(r.getSourcesFailed()).isEqualTo(1);
    assertThat(r.getStatus()).isEqualTo(RefreshStatus.PARTIAL);
    assertThat(service.isReady()).isTrue();
  }

  @Test
  void refresh_allSourcesOk_recordsCountersAndSuccess() {
    when(mirror.sync()).thenReturn(new TrustedListMirrorPort.Diff(0, 0, 10));
    LOTLInfo lotl = lotlInfo(List.of(tlInfo(false)));
    TLInfo otherTl = tlInfo(false);
    TLValidationJobSummary summary = mock(TLValidationJobSummary.class);
    when(summary.getLOTLInfos()).thenReturn(List.of(lotl));
    when(summary.getOtherTLInfos()).thenReturn(List.of(otherTl));
    when(job.getSummary()).thenReturn(summary);

    TslRefresh r = service.refresh(RefreshTrigger.SCHEDULED, null);

    assertThat(r.getSourcesTotal()).isEqualTo(3);
    assertThat(r.getSourcesFailed()).isZero();
    assertThat(r.getStatus()).isEqualTo(RefreshStatus.SUCCESS);
  }
}
