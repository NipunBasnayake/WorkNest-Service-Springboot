package com.worknest.tenant.dto.chat;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class HrConversationTargetsResponseDto {
    private List<EmployeeSimpleDto> hrTargets;
    private List<EmployeeSimpleDto> employeeTargets;
}
