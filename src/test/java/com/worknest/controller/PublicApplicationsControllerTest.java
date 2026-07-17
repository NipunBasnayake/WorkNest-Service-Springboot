package com.worknest.controller;

import com.worknest.common.exception.GlobalExceptionHandler;
import com.worknest.publicapi.dto.PublicApplicationRequestDto;
import com.worknest.publicapi.dto.PublicApplicationResponseDto;
import com.worknest.publicapi.service.PublicCandidateApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PublicApplicationsControllerTest {

    private PublicCandidateApplicationService applicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(PublicCandidateApplicationService.class);
        mockMvc = standaloneSetup(new PublicApplicationsController(applicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void bindsJsonApplicationAndResumeParts() throws Exception {
        when(applicationService.apply(eq("acme"), eq("software-engineer"), any()))
                .thenReturn(PublicApplicationResponseDto.builder()
                        .referenceNumber("APP-123")
                        .jobSlug("software-engineer")
                        .build());
        MockMultipartFile application = new MockMultipartFile(
                "application",
                "application.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"ada@example.com\"}".getBytes());
        MockMultipartFile resume = new MockMultipartFile(
                "resume",
                "resume.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-test".getBytes());

        mockMvc.perform(multipart("/api/public/{tenantSlug}/careers/{jobSlug}/apply", "acme", "software-engineer")
                        .file(application)
                        .file(resume))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referenceNumber").value("APP-123"));

        ArgumentCaptor<PublicApplicationRequestDto> requestCaptor =
                ArgumentCaptor.forClass(PublicApplicationRequestDto.class);
        verify(applicationService).apply(eq("acme"), eq("software-engineer"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFirstName()).isEqualTo("Ada");
        assertThat(requestCaptor.getValue().getLastName()).isEqualTo("Lovelace");
        assertThat(requestCaptor.getValue().getResume().getOriginalFilename()).isEqualTo("resume.pdf");
    }

    @Test
    void rejectsTopLevelJsonInsteadOfTreatingItAsMultipart() throws Exception {
        mockMvc.perform(post("/api/public/{tenantSlug}/careers/{jobSlug}/apply", "acme", "software-engineer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void missingResumePartReturnsBadRequest() throws Exception {
        MockMultipartFile application = new MockMultipartFile(
                "application",
                "application.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"ada@example.com\"}".getBytes());

        mockMvc.perform(multipart("/api/public/{tenantSlug}/careers/{jobSlug}/apply", "acme", "software-engineer")
                        .file(application))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_MULTIPART_PART"));
    }
}
