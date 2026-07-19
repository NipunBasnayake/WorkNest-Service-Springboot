package com.worknest.tenant.service;

import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.storage.FileStorageService;
import com.worknest.common.storage.ImageAssetProcessor;
import com.worknest.common.storage.StorageCategory;
import com.worknest.common.storage.StoredImageAssetDto;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.employee.EmployeeAvatarDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.StoredFileMetadata;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.StoredFileMetadataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Transactional(transactionManager = "transactionManager")
public class EmployeeAvatarService {

    private final EmployeeRepository employeeRepository;
    private final StoredFileMetadataRepository storedFileMetadataRepository;
    private final FileStorageService fileStorageService;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    public EmployeeAvatarService(
            EmployeeRepository employeeRepository,
            StoredFileMetadataRepository storedFileMetadataRepository,
            FileStorageService fileStorageService,
            AuthorizationService authorizationService,
            AuditLogService auditLogService) {
        this.employeeRepository = employeeRepository;
        this.storedFileMetadataRepository = storedFileMetadataRepository;
        this.fileStorageService = fileStorageService;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
    }

    public EmployeeAvatarDto uploadOwnAvatar(MultipartFile file) {
        authorizationService.requirePermission(Permission.MANAGE_SELF_PROFILE);
        return replaceAvatar(authorizationService.getCurrentEmployeeOrThrow(), file);
    }

    public EmployeeAvatarDto deleteOwnAvatar() {
        authorizationService.requirePermission(Permission.MANAGE_SELF_PROFILE);
        return removeAvatar(authorizationService.getCurrentEmployeeOrThrow());
    }

    public EmployeeAvatarDto uploadManagedAvatar(Long employeeId, MultipartFile file) {
        authorizationService.requirePermission(Permission.MANAGE_EMPLOYEE);
        return replaceAvatar(requireEmployee(employeeId), file);
    }

    public EmployeeAvatarDto deleteManagedAvatar(Long employeeId) {
        authorizationService.requirePermission(Permission.MANAGE_EMPLOYEE);
        return removeAvatar(requireEmployee(employeeId));
    }

    private EmployeeAvatarDto replaceAvatar(Employee employee, MultipartFile file) {
        StoredFileMetadata previous = employee.getAvatarAsset();
        StoredImageAssetDto asset = fileStorageService.storeImageAsset(
                file,
                ImageAssetProcessor.Profile.AVATAR,
                StorageCategory.EMPLOYEE_AVATAR,
                "EMPLOYEE",
                employee.getId()
        );
        StoredFileMetadata metadata = storedFileMetadataRepository.findById(Long.valueOf(asset.assetId()))
                .orElseThrow(() -> new ResourceNotFoundException("Avatar asset was not created"));
        employee.setAvatarAsset(metadata);
        employeeRepository.save(employee);
        if (previous != null && !previous.getId().equals(metadata.getId())) {
            fileStorageService.supersedeImageAsset(previous);
        }
        auditLogService.logAction(
                AuditActionType.UPLOAD,
                AuditEntityType.EMPLOYEE,
                employee.getId(),
                "{\"field\":\"avatar\",\"assetId\":\"" + metadata.getId() + "\"}"
        );
        return new EmployeeAvatarDto(
                employee.getId(),
                asset.assetId(),
                asset.variants().getOrDefault("256", asset.url()),
                asset.variants(),
                LocalDateTime.now()
        );
    }

    private EmployeeAvatarDto removeAvatar(Employee employee) {
        StoredFileMetadata previous = employee.getAvatarAsset();
        employee.setAvatarAsset(null);
        employeeRepository.save(employee);
        if (previous != null) fileStorageService.supersedeImageAsset(previous);
        auditLogService.logAction(
                AuditActionType.DELETE,
                AuditEntityType.EMPLOYEE,
                employee.getId(),
                "{\"field\":\"avatar\"}"
        );
        return new EmployeeAvatarDto(employee.getId(), null, null, Map.of(), LocalDateTime.now());
    }

    private Employee requireEmployee(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            throw new ResourceNotFoundException("Employee not found");
        }
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }
}
