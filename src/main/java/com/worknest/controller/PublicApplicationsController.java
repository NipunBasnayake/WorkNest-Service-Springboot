package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.publicapi.dto.PublicApplicationRequestDto;
import com.worknest.publicapi.dto.PublicApplicationResponseDto;
import com.worknest.publicapi.dto.PublicApplicationStatusDto;
import com.worknest.publicapi.service.PublicCandidateApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/public/{tenantSlug}")
public class PublicApplicationsController {

    private final PublicCandidateApplicationService publicCandidateApplicationService;

    public PublicApplicationsController(PublicCandidateApplicationService publicCandidateApplicationService) {
        this.publicCandidateApplicationService = publicCandidateApplicationService;
    }

    @PostMapping(value = "/careers/{jobSlug}/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PublicApplicationResponseDto>> apply(
            @PathVariable("tenantSlug") String tenantSlug,
            @PathVariable("jobSlug") String jobSlug,
            @Valid @ModelAttribute PublicApplicationRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success(
                "Application submitted successfully",
                publicCandidateApplicationService.apply(tenantSlug, jobSlug, requestDto)));
    }

    @GetMapping("/applications/{referenceNumber}")
    public ResponseEntity<ApiResponse<PublicApplicationStatusDto>> getApplicationStatus(
            @PathVariable("tenantSlug") String tenantSlug,
            @PathVariable("referenceNumber") String referenceNumber) {
        return ResponseEntity.ok(ApiResponse.success(
                "Application status retrieved successfully",
                publicCandidateApplicationService.getStatus(tenantSlug, referenceNumber)));
    }
}
