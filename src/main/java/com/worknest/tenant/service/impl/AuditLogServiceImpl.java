package com.worknest.tenant.service.impl;

import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.audit.AuditLogResponseDto;
import com.worknest.tenant.entity.AuditLog;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.repository.AuditLogRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(transactionManager = "transactionManager")
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final EmployeeRepository employeeRepository;
    private final SecurityUtils securityUtils;
    private final TenantDtoMapper tenantDtoMapper;

    public AuditLogServiceImpl(
            AuditLogRepository auditLogRepository,
            EmployeeRepository employeeRepository,
            SecurityUtils securityUtils,
            TenantDtoMapper tenantDtoMapper) {
        this.auditLogRepository = auditLogRepository;
        this.employeeRepository = employeeRepository;
        this.securityUtils = securityUtils;
        this.tenantDtoMapper = tenantDtoMapper;
    }

    @Override
    public void logAction(AuditActionType action, AuditEntityType entityType, Long entityId, String metadataJson) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setMetadataJson(metadataJson);

        try {
            String currentEmail = securityUtils.getCurrentUserEmailOrThrow();
            auditLog.setActorEmail(currentEmail);
            employeeRepository.findByEmailIgnoreCase(currentEmail).ifPresent(auditLog::setActor);
        } catch (ForbiddenOperationException ex) {
            auditLog.setActorEmail("system");
        }

        auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<AuditLogResponseDto> search(
            AuditActionType action,
            AuditEntityType entityType,
            Long actorId,
            LocalDateTime fromDate,
            LocalDateTime toDate) {

        return auditLogRepository.search(action, entityType, actorId, fromDate, toDate)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<AuditLogResponseDto> searchPaged(
            AuditActionType action,
            AuditEntityType entityType,
            Long actorId,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Page<AuditLog> resultPage = auditLogRepository.searchPage(
                action,
                entityType,
                actorId,
                fromDate,
                toDate,
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
        );

        return PagedResultDto.<AuditLogResponseDto>builder()
                .items(resultPage.getContent().stream().map(this::toResponse).toList())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    private AuditLogResponseDto toResponse(AuditLog auditLog) {
        return AuditLogResponseDto.builder()
                .id(auditLog.getId())
                .actor(tenantDtoMapper.toEmployeeSimple(auditLog.getActor()))
                .actorEmail(auditLog.getActorEmail())
                .action(auditLog.getAction())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .metadataJson(auditLog.getMetadataJson())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
