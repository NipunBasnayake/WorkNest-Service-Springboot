package com.worknest.tenant.dto.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeSelfUpdateDto {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Size(max = 120, message = "Designation must not exceed 120 characters")
    private String designation;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    @Pattern(
            regexp = "^[0-9+()\\-\\s]*$",
            message = "Phone can contain digits, spaces, and +()- symbols only"
    )
    private String phone;

    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;
}
