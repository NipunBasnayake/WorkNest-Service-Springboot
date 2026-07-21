package com.worknest.publicapi.service.impl;

import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.dto.TenantBrandingViewDto;
import com.worknest.master.service.TenantBrandingService;
import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.repository.JobPositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCareersServiceImplTest {

    @Mock private JobPositionRepository jobPositionRepository;
    @Mock private TenantBrandingService tenantBrandingService;

    private PublicCareersServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PublicCareersServiceImpl(jobPositionRepository, tenantBrandingService);
        when(tenantBrandingService.getPublicBranding("residue-solutions"))
                .thenReturn(new TenantBrandingViewDto(
                        1L,
                        "residue-solutions",
                        "residue-solutions",
                        "Residue Solutions",
                        "#7c3aed",
                        1L,
                        TenantStatus.ACTIVE,
                        LocalDateTime.now()));
    }

    @Test
    void resolvesLegacyPublishedSlugWithoutRewritingDots() {
        JobPosition position = new JobPosition();
        position.setId(4L);
        position.setSlug("devops.engineer-2026-4");
        position.setTitle("DevOps Engineer");
        when(jobPositionRepository.findPublishedJobBySlug("devops.engineer-2026-4"))
                .thenReturn(Optional.of(position));
        when(jobPositionRepository.findPublishedJobs()).thenReturn(List.of(position));

        var result = service.getPublishedCareer("residue-solutions", "DevOps.Engineer-2026-4");

        assertThat(result.getSlug()).isEqualTo("devops.engineer-2026-4");
        assertThat(result.getTitle()).isEqualTo("DevOps Engineer");
        verify(jobPositionRepository).findPublishedJobBySlug("devops.engineer-2026-4");
    }

    @Test
    void fallsBackToCanonicalSlugForModernRecords() {
        JobPosition position = new JobPosition();
        position.setId(5L);
        position.setSlug("devopsengineer-2026-5");
        position.setTitle("DevOps Engineer");
        when(jobPositionRepository.findPublishedJobBySlug("devops.engineer-2026-5"))
                .thenReturn(Optional.empty());
        when(jobPositionRepository.findPublishedJobBySlug("devopsengineer-2026-5"))
                .thenReturn(Optional.of(position));
        when(jobPositionRepository.findPublishedJobs()).thenReturn(List.of(position));

        var result = service.getPublishedCareer("residue-solutions", "DevOps.Engineer-2026-5");

        assertThat(result.getSlug()).isEqualTo("devopsengineer-2026-5");
        verify(jobPositionRepository).findPublishedJobBySlug("devops.engineer-2026-5");
        verify(jobPositionRepository).findPublishedJobBySlug("devopsengineer-2026-5");
    }

    @Test
    void unknownPublishedSlugReturnsNotFound() {
        when(jobPositionRepository.findPublishedJobBySlug("unknown-role")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublishedCareer("residue-solutions", "unknown-role"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job vacancy not found");
    }
}
