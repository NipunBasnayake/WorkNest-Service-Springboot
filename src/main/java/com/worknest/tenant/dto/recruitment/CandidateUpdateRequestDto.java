package com.worknest.tenant.dto.recruitment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CandidateUpdateRequestDto {

    @NotBlank(message = "Candidate name is required")
    @Size(max = 180, message = "Candidate name must not exceed 180 characters")
    private String fullName;

    @NotBlank(message = "Candidate email is required")
    @Email(message = "Candidate email must be valid")
    @Size(max = 255, message = "Candidate email must not exceed 255 characters")
    private String email;

    @Size(max = 30, message = "Candidate phone must not exceed 30 characters")
    private String phone;

    @Size(max = 160, message = "Current title must not exceed 160 characters")
    private String currentTitle;

    private Integer yearsOfExperience;

    @Size(max = 120, message = "Source must not exceed 120 characters")
    private String source;

    @Size(max = 5000, message = "Summary must not exceed 5000 characters")
    private String summary;
}