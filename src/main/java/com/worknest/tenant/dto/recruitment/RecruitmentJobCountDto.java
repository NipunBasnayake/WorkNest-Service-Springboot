package com.worknest.tenant.dto.recruitment;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RecruitmentJobCountDto {
    private Long jobPositionId;
    private String title;
    private long count;
}