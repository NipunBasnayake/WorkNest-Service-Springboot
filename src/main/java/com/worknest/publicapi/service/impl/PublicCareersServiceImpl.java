package com.worknest.publicapi.service.impl;

import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.util.SlugUtils;
import com.worknest.master.dto.TenantBrandingViewDto;
import com.worknest.master.service.TenantBrandingService;
import com.worknest.publicapi.dto.PublicCareerJobDetailDto;
import com.worknest.publicapi.dto.PublicCareerJobSummaryDto;
import com.worknest.publicapi.dto.PublicCareersResponseDto;
import com.worknest.publicapi.dto.PublicCompanyDto;
import com.worknest.publicapi.service.PublicCareersService;
import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.repository.JobPositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(transactionManager = "transactionManager", readOnly = true)
public class PublicCareersServiceImpl implements PublicCareersService {

    private static final int SUMMARY_MAX_LENGTH = 240;

    private final JobPositionRepository jobPositionRepository;
    private final TenantBrandingService tenantBrandingService;

    public PublicCareersServiceImpl(
            JobPositionRepository jobPositionRepository,
            TenantBrandingService tenantBrandingService) {
        this.jobPositionRepository = jobPositionRepository;
        this.tenantBrandingService = tenantBrandingService;
    }

    @Override
    public PublicCareersResponseDto listPublishedCareers(String tenantSlug) {
        PublicCompanyDto company = toCompanyDto(tenantBrandingService.getPublicBranding(tenantSlug));
        return PublicCareersResponseDto.builder()
                .company(company)
                .jobs(jobPositionRepository.findPublishedJobs().stream()
                        .map(this::toSummaryDto)
                        .toList())
                .build();
    }

    @Override
    public PublicCareerJobDetailDto getPublishedCareer(String tenantSlug, String jobSlug) {
        PublicCompanyDto company = toCompanyDto(tenantBrandingService.getPublicBranding(tenantSlug));
        String normalizedSlug = SlugUtils.slugify(jobSlug);
        if (normalizedSlug == null) {
            throw new ResourceNotFoundException("Job vacancy not found");
        }

        JobPosition position = jobPositionRepository.findPublishedJobBySlug(normalizedSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Job vacancy not found"));
        return toDetailDto(company, position);
    }

    private PublicCompanyDto toCompanyDto(TenantBrandingViewDto branding) {
        return PublicCompanyDto.builder()
                .tenantSlug(branding.tenantSlug())
                .companyName(branding.companyName())
                .logoUrl(branding.logo() == null ? null : branding.logo().url())
                .about("Explore current opportunities at " + branding.companyName() + ".")
                .build();
    }

    private PublicCareerJobSummaryDto toSummaryDto(JobPosition position) {
        return PublicCareerJobSummaryDto.builder()
                .slug(position.getSlug())
                .title(position.getTitle())
                .department(position.getDepartment())
                .employmentType(position.getEmploymentType())
                .location(position.getLocation())
                .experience(position.getExperience())
                .salary(position.getSalary())
                .summary(firstNonBlank(position.getSummary(), summarize(position.getDescription())))
                .postedDate(position.getPublishedAt() == null ? position.getCreatedAt() : position.getPublishedAt())
                .expiry(position.getExpiresAt())
                .build();
    }

    private PublicCareerJobDetailDto toDetailDto(PublicCompanyDto company, JobPosition position) {
        return PublicCareerJobDetailDto.builder()
                .company(company)
                .slug(position.getSlug())
                .title(position.getTitle())
                .department(position.getDepartment())
                .employmentType(position.getEmploymentType())
                .location(position.getLocation())
                .experience(position.getExperience())
                .salary(position.getSalary())
                .summary(firstNonBlank(position.getSummary(), summarize(position.getDescription())))
                .description(position.getDescription())
                .responsibilities(position.getResponsibilities())
                .requirements(position.getRequirements())
                .benefits(position.getBenefits())
                .postedDate(position.getPublishedAt() == null ? position.getCreatedAt() : position.getPublishedAt())
                .expiry(position.getExpiresAt())
                .relatedJobs(jobPositionRepository.findPublishedJobs().stream()
                        .filter(item -> !item.getId().equals(position.getId()))
                        .sorted((left, right) -> Boolean.compare(
                                java.util.Objects.equals(right.getDepartment(), position.getDepartment()),
                                java.util.Objects.equals(left.getDepartment(), position.getDepartment())))
                        .limit(3)
                        .map(this::toSummaryDto)
                        .toList())
                .build();
    }

    private String summarize(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() <= SUMMARY_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, SUMMARY_MAX_LENGTH - 1).trim() + "...";
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = trimToNull(first);
        return normalizedFirst == null ? trimToNull(second) : normalizedFirst;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
