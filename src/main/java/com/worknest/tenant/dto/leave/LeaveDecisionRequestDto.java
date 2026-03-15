package com.worknest.tenant.dto.leave;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeaveDecisionRequestDto {

    @NotNull(message = "Approver employee ID is required")
    private Long approverEmployeeId;

    @Size(max = 2000, message = "Reason must not exceed 2000 characters")
    private String reason;
}
