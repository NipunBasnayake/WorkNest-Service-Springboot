package com.worknest.master.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TenantRegistrationRequestDto {

    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name must not exceed 255 characters")
    private String companyName;

    @NotBlank(message = "Tenant key is required")
    @Pattern(
            regexp = "^[a-z0-9_-]{3,50}$",
            message = "Tenant key must be 3-50 chars and contain only lowercase letters, numbers, underscore, or hyphen"
    )
    private String tenantKey;

    @NotBlank(message = "Tenant admin name is required")
    @Size(max = 150, message = "Tenant admin name must not exceed 150 characters")
    private String adminFullName;

    @NotBlank(message = "Tenant admin email is required")
    @Email(message = "Tenant admin email must be valid")
    @Size(max = 255, message = "Tenant admin email must not exceed 255 characters")
    private String adminEmail;

    @NotBlank(message = "Tenant admin password is required")
    @Size(min = 8, max = 100, message = "Tenant admin password must be between 8 and 100 characters")
    private String adminPassword;
}
