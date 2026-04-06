package com.worknest.tenant.dto.leave;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeaveDecisionRequestDto {

    @JsonAlias("reason")
    @Size(max = 2000, message = "Decision comment must not exceed 2000 characters")
    private String decisionComment;
}
