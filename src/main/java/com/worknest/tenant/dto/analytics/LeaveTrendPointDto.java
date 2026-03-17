package com.worknest.tenant.dto.analytics;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LeaveTrendPointDto {
    private String month;
    private long total;
    private long pending;
    private long approved;
    private long rejected;
}
