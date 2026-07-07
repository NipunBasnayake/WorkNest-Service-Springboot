package com.worknest.tenant.dto.recruitment;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class CandidateResponseDto {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String currentTitle;
    private Integer yearsOfExperience;
    private String source;
    private String summary;
    private String resumeFileName;
    private String resumeFileUrl;
    private String resumeMimeType;
    private Long resumeFileSizeBytes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}