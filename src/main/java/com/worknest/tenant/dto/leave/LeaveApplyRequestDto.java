package com.worknest.tenant.dto.leave;

import com.worknest.tenant.enums.LeaveType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class LeaveApplyRequestDto {

    @NotNull(message = "Leave type is required")
    private LeaveType leaveType;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @Size(max = 2000, message = "Reason must not exceed 2000 characters")
    private String reason;

    @Valid
    @Size(max = 20, message = "You can attach up to 20 supporting files")
    private List<LeaveAttachmentRequestDto> attachments;
}
