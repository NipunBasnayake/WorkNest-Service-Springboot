package com.worknest.tenant.dto.employee;

import java.time.LocalDateTime;
import java.util.Map;

public record EmployeeAvatarDto(
        Long employeeId,
        String assetId,
        String avatarUrl,
        Map<String, String> variants,
        LocalDateTime updatedAt) {
}
