package com.worknest.publicapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PublicApplicationRequestDto {

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

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;

    @Size(max = 120, message = "Current city must not exceed 120 characters")
    private String currentCity;

    @Size(max = 120, message = "Country must not exceed 120 characters")
    private String country;

    @Size(max = 500, message = "LinkedIn URL must not exceed 500 characters")
    private String linkedIn;

    @Size(max = 500, message = "Portfolio URL must not exceed 500 characters")
    private String portfolio;

    @Min(value = 0, message = "Years of experience must not be negative")
    @Max(value = 80, message = "Years of experience must not exceed 80")
    private Integer yearsOfExperience;

    @Size(max = 160, message = "Current company must not exceed 160 characters")
    private String currentCompany;

    @Size(max = 160, message = "Current position must not exceed 160 characters")
    private String currentPosition;

    @DecimalMin(value = "0.0", inclusive = true, message = "Expected salary must not be negative")
    private BigDecimal expectedSalary;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate availableFrom;

    @Size(max = 5000, message = "Cover letter must not exceed 5000 characters")
    private String coverLetter;

    private MultipartFile resume;
}
