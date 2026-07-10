package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.publicapi.dto.PublicCareerJobDetailDto;
import com.worknest.publicapi.dto.PublicCareersResponseDto;
import com.worknest.publicapi.service.PublicCareersService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/public/{tenantSlug}/careers")
public class PublicCareersController {

    private final PublicCareersService publicCareersService;

    public PublicCareersController(PublicCareersService publicCareersService) {
        this.publicCareersService = publicCareersService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PublicCareersResponseDto>> listCareers(
            @PathVariable("tenantSlug") String tenantSlug) {
        return ResponseEntity.ok(ApiResponse.success(
                "Public job vacancies retrieved successfully",
                publicCareersService.listPublishedCareers(tenantSlug)));
    }

    @GetMapping("/{jobSlug}")
    public ResponseEntity<ApiResponse<PublicCareerJobDetailDto>> getCareer(
            @PathVariable("tenantSlug") String tenantSlug,
            @PathVariable("jobSlug") String jobSlug) {
        return ResponseEntity.ok(ApiResponse.success(
                "Public job vacancy retrieved successfully",
                publicCareersService.getPublishedCareer(tenantSlug, jobSlug)));
    }
}
