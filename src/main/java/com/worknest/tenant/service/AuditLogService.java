package com.worknest.tenant.service;

import com.worknest.tenant.dto.audit.AuditLogResponseDto;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogService {

    void logAction(AuditActionType action, AuditEntityType entityType, Long entityId, String metadataJson);

    List<AuditLogResponseDto> search(
            AuditActionType action,
            AuditEntityType entityType,
            Long actorId,
            LocalDateTime fromDate,
            LocalDateTime toDate);

    PagedResultDto<AuditLogResponseDto> searchPaged(
            AuditActionType action,
            AuditEntityType entityType,
            Long actorId,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            int page,
            int size,
            String sortBy,
            String sortDir);
}
