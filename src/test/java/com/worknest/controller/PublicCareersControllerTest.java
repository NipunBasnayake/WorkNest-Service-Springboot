package com.worknest.controller;

import com.worknest.publicapi.dto.PublicCareerJobDetailDto;
import com.worknest.publicapi.service.PublicCareersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicCareersControllerTest {

    @Mock private PublicCareersService publicCareersService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicCareersController(publicCareersService)).build();
    }

    @Test
    void dottedJobSlugReachesThePublicDetailServiceUnchanged() throws Exception {
        when(publicCareersService.getPublishedCareer("acme", "devops.engineer-2026-4"))
                .thenReturn(PublicCareerJobDetailDto.builder()
                        .slug("devops.engineer-2026-4")
                        .title("DevOps Engineer")
                        .build());

        mockMvc.perform(get("/api/public/{tenantSlug}/careers/{jobSlug}",
                        "acme",
                        "devops.engineer-2026-4"))
                .andExpect(status().isOk());

        verify(publicCareersService).getPublishedCareer("acme", "devops.engineer-2026-4");
    }
}
