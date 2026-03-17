package com.worknest.tenant.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class StatusCountDto {
    private String status;
    private long count;
}
