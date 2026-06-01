package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class CandidateCommentResponseDto {
    private Long id;
    private Long candidateId;
    private EmployeeSimpleDto author;
    private String message;
    private LocalDateTime createdAt;
}