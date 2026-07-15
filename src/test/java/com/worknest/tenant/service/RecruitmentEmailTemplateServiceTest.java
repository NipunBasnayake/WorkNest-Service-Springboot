package com.worknest.tenant.service;

import com.worknest.common.exception.BadRequestException;
import com.worknest.notification.email.EmailContent;
import com.worknest.notification.email.EmailDispatchService;
import com.worknest.tenant.entity.Candidate;
import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.entity.RecruitmentEmailLog;
import com.worknest.tenant.entity.RecruitmentEmailTemplate;
import com.worknest.tenant.enums.RecruitmentEmailTemplateType;
import com.worknest.tenant.repository.RecruitmentEmailLogRepository;
import com.worknest.tenant.repository.RecruitmentEmailTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentEmailTemplateServiceTest {

    @Mock
    private RecruitmentEmailTemplateRepository templateRepository;
    @Mock
    private RecruitmentEmailLogRepository logRepository;
    @Mock
    private EmailDispatchService emailDispatchService;

    private Map<RecruitmentEmailTemplateType, RecruitmentEmailTemplate> templates;
    private RecruitmentEmailTemplateService service;

    @BeforeEach
    void setUp() {
        templates = new EnumMap<>(RecruitmentEmailTemplateType.class);
        service = new RecruitmentEmailTemplateService(templateRepository, logRepository, emailDispatchService);
        when(templateRepository.findByType(any())).thenAnswer(invocation -> Optional.ofNullable(templates.get(invocation.getArgument(0))));
        lenient().when(templateRepository.save(any(RecruitmentEmailTemplate.class))).thenAnswer(invocation -> {
            RecruitmentEmailTemplate template = invocation.getArgument(0);
            template.setId((long) templates.size() + 1);
            template.setUpdatedAt(LocalDateTime.of(2026, 7, 15, 10, 0));
            templates.put(template.getType(), template);
            return template;
        });
        lenient().when(logRepository.save(any(RecruitmentEmailLog.class))).thenAnswer(invocation -> {
            RecruitmentEmailLog log = invocation.getArgument(0);
            log.setId(99L);
            log.setSentAt(LocalDateTime.of(2026, 7, 15, 10, 30));
            return log;
        });
    }

    @Test
    void createsTheSevenSimpleHiringTemplatesWithSupportedVariables() {
        var result = service.listTemplates();

        assertThat(result).hasSize(7);
        assertThat(result).extracting(item -> item.getType())
                .containsExactly(RecruitmentEmailTemplateType.values());
        assertThat(result.getFirst().getAvailableVariables())
                .contains("candidateName", "jobTitle", "companyName", "interviewDate", "careersLink");
    }

    @Test
    void replacesVariablesAndQueuesAProfessionalCandidateEmail() {
        CandidateApplication application = application();

        service.send(application, RecruitmentEmailTemplateType.APPLICATION_RECEIVED,
                "Acme Software", "https://worknest.app/acme/careers", null);

        ArgumentCaptor<EmailContent> content = ArgumentCaptor.forClass(EmailContent.class);
        verify(emailDispatchService).sendHtmlEmailAsync(
                eq("alex@example.com"), content.capture());
        assertThat(content.getValue().subject()).isEqualTo("Application received - Frontend Developer");
        assertThat(content.getValue().htmlBody())
                .contains("Hi Alex Perera", "Acme Software", "https://worknest.app/acme/careers")
                .doesNotContain("{{candidateName}}", "{{jobTitle}}");
        verify(logRepository).save(any(RecruitmentEmailLog.class));
    }

    @Test
    void refusesToSendADisabledTemplate() {
        RecruitmentEmailTemplate disabled = new RecruitmentEmailTemplate();
        disabled.setType(RecruitmentEmailTemplateType.REJECTED);
        disabled.setSubject("Application update");
        disabled.setBodyMarkdown("Thank you");
        disabled.setEnabled(false);
        templates.put(disabled.getType(), disabled);

        assertThatThrownBy(() -> service.send(application(), disabled.getType(), "Acme", "https://example.com", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("disabled");
        verify(emailDispatchService, never()).sendHtmlEmailAsync(any(), any());
    }

    private CandidateApplication application() {
        Candidate candidate = new Candidate();
        candidate.setFullName("Alex Perera");
        candidate.setEmail("alex@example.com");
        JobPosition job = new JobPosition();
        job.setTitle("Frontend Developer");
        CandidateApplication application = new CandidateApplication();
        application.setId(10L);
        application.setCandidate(candidate);
        application.setJobPosition(job);
        return application;
    }
}
