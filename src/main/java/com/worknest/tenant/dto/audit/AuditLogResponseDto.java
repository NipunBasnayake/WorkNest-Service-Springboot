package com.worknest.tenant.dto.audit;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class AuditLogResponseDto {
    private Long id;
    private EmployeeSimpleDto actor;
    private String actorEmail;
    private AuditActionType action;
    private AuditEntityType entityType;
    private Long entityId;
    private String metadataJson;
    private LocalDateTime createdAt;
}
