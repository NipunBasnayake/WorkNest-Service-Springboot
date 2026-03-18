package com.worknest.tenant.dto.employee;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class EmployeeCreateRequestDto {

    @Size(max = 30, message = "Employee code must not exceed 30 characters")
    private String employeeCode;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    @NotNull(message = "Role is required")
    private PlatformRole role;

    @Size(max = 120, message = "Designation must not exceed 120 characters")
    private String designation;

    @Size(max = 120, message = "Department must not exceed 120 characters")
    private String department;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    @Pattern(
            regexp = "^[0-9+()\\-\\s]*$",
            message = "Phone can contain digits, spaces, and +()- symbols only"
    )
    private String phone;

    @DecimalMin(value = "0.00", message = "Salary cannot be negative")
    @Digits(integer = 12, fraction = 2, message = "Salary must be a valid amount with up to 2 decimals")
    private BigDecimal salary;

    @NotNull(message = "Joined date is required")
    @PastOrPresent(message = "Joined date cannot be in the future")
    private LocalDate joinedDate;

    private UserStatus status;
}
