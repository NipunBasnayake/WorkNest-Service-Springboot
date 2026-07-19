package com.worknest.master.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BrandingUpdateRequestDto {

    @Size(min = 1, max = 255, message = "Company name must be between 1 and 255 characters")
    private String companyName;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Primary color must use #RRGGBB format")
    private String primaryColor;

    private Long brandingVersion;
}
