package com.worknest.tenant.service;

import com.worknest.notification.email.EmailContent;
import com.worknest.notification.email.EmailDispatchService;
import com.worknest.notification.email.BrandContext;
import com.worknest.notification.email.BrandContextResolver;
import com.worknest.common.exception.BadRequestException;
import com.worknest.tenant.dto.recruitment.RecruitmentEmailLogResponseDto;
import com.worknest.tenant.dto.recruitment.RecruitmentEmailTemplateResponseDto;
import com.worknest.tenant.dto.recruitment.RecruitmentEmailTemplateUpdateRequestDto;
import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.entity.Interview;
import com.worknest.tenant.entity.RecruitmentEmailLog;
import com.worknest.tenant.entity.RecruitmentEmailTemplate;
import com.worknest.tenant.enums.RecruitmentEmailTemplateType;
import com.worknest.tenant.repository.RecruitmentEmailLogRepository;
import com.worknest.tenant.repository.RecruitmentEmailTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(transactionManager = "transactionManager")
public class RecruitmentEmailTemplateService {

    private static final List<String> VARIABLES = List.of(
            "candidateName", "jobTitle", "companyName", "interviewDate", "interviewTime",
            "interviewMode", "meetingLink", "location", "careersLink");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9]*)}}" );
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^]]+)]\\((https?://[^ )]+)\\)");

    private final RecruitmentEmailTemplateRepository templateRepository;
    private final RecruitmentEmailLogRepository logRepository;
    private final EmailDispatchService emailDispatchService;
    private final BrandContextResolver brandContextResolver;

    @Autowired
    public RecruitmentEmailTemplateService(
            RecruitmentEmailTemplateRepository templateRepository,
            RecruitmentEmailLogRepository logRepository,
            EmailDispatchService emailDispatchService,
            BrandContextResolver brandContextResolver) {
        this.templateRepository = templateRepository;
        this.logRepository = logRepository;
        this.emailDispatchService = emailDispatchService;
        this.brandContextResolver = brandContextResolver;
    }

    public RecruitmentEmailTemplateService(
            RecruitmentEmailTemplateRepository templateRepository,
            RecruitmentEmailLogRepository logRepository,
            EmailDispatchService emailDispatchService) {
        this(templateRepository, logRepository, emailDispatchService, null);
    }

    public List<RecruitmentEmailTemplateResponseDto> listTemplates() {
        ensureDefaults();
        return java.util.Arrays.stream(RecruitmentEmailTemplateType.values())
                .map(type -> templateRepository.findByType(type).orElseThrow())
                .map(this::toResponse)
                .toList();
    }

    public RecruitmentEmailTemplateResponseDto updateTemplate(
            RecruitmentEmailTemplateType type,
            RecruitmentEmailTemplateUpdateRequestDto request) {
        RecruitmentEmailTemplate template = findOrCreate(type);
        template.setSubject(request.getSubject().trim());
        template.setBodyMarkdown(request.getBodyMarkdown().trim());
        template.setEnabled(request.getEnabled() == null || request.getEnabled());
        return toResponse(templateRepository.save(template));
    }

    public RecruitmentEmailLogResponseDto send(
            CandidateApplication application,
            RecruitmentEmailTemplateType type,
            String companyName,
            String careersLink,
            Interview interview) {
        RecruitmentEmailTemplate template = findOrCreate(type);
        if (!template.isEnabled()) {
            throw new BadRequestException("The selected recruitment email template is disabled");
        }

        Map<String, String> variables = buildVariables(application, companyName, careersLink, interview);
        String subject = replaceVariables(template.getSubject(), variables);
        String body = replaceVariables(template.getBodyMarkdown(), variables);
        EmailContent email = new EmailContent(subject, wrapEmail(subject, renderMarkdown(body), resolveBrand(companyName)));
        emailDispatchService.sendHtmlEmailAsync(application.getCandidate().getEmail(), email);

        RecruitmentEmailLog log = new RecruitmentEmailLog();
        log.setApplication(application);
        log.setTemplateType(type);
        log.setRecipientEmail(application.getCandidate().getEmail());
        log.setSubject(subject);
        log.setDeliveryStatus("QUEUED");
        return toLogResponse(logRepository.save(log));
    }

    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<RecruitmentEmailLogResponseDto> listApplicationEmails(Long applicationId) {
        return logRepository.findByApplicationIdOrderBySentAtDesc(applicationId).stream()
                .map(this::toLogResponse)
                .toList();
    }

    private void ensureDefaults() {
        defaultTemplates().forEach((type, defaults) -> {
            if (templateRepository.findByType(type).isEmpty()) {
                RecruitmentEmailTemplate template = new RecruitmentEmailTemplate();
                template.setType(type);
                template.setSubject(defaults.subject());
                template.setBodyMarkdown(defaults.body());
                template.setEnabled(true);
                templateRepository.save(template);
            }
        });
    }

    private RecruitmentEmailTemplate findOrCreate(RecruitmentEmailTemplateType type) {
        return templateRepository.findByType(type).orElseGet(() -> {
            TemplateDefaults defaults = defaultTemplates().get(type);
            RecruitmentEmailTemplate template = new RecruitmentEmailTemplate();
            template.setType(type);
            template.setSubject(defaults.subject());
            template.setBodyMarkdown(defaults.body());
            template.setEnabled(true);
            return templateRepository.save(template);
        });
    }

    private Map<RecruitmentEmailTemplateType, TemplateDefaults> defaultTemplates() {
        Map<RecruitmentEmailTemplateType, TemplateDefaults> defaults = new EnumMap<>(RecruitmentEmailTemplateType.class);
        defaults.put(RecruitmentEmailTemplateType.APPLICATION_RECEIVED, new TemplateDefaults(
                "Application received - {{jobTitle}}",
                "# Thank you for applying\n\nHi {{candidateName}},\n\nWe received your application for **{{jobTitle}}** at {{companyName}}. Our HR team will review it and contact you if we need anything else.\n\n[View our careers page]({{careersLink}})"));
        defaults.put(RecruitmentEmailTemplateType.SHORTLISTED, new TemplateDefaults(
                "Your application has been shortlisted - {{jobTitle}}",
                "# You have been shortlisted\n\nHi {{candidateName}},\n\nWe are pleased to let you know that your application for **{{jobTitle}}** has been shortlisted. We will contact you with the next step shortly."));
        defaults.put(RecruitmentEmailTemplateType.INTERVIEW_INVITATION, new TemplateDefaults(
                "Interview invitation - {{jobTitle}}",
                "# Interview invitation\n\nHi {{candidateName}},\n\nWe would like to invite you to an interview for **{{jobTitle}}**.\n\n- **Date:** {{interviewDate}}\n- **Time:** {{interviewTime}}\n- **Mode:** {{interviewMode}}\n- **Meeting link:** {{meetingLink}}\n- **Location:** {{location}}"));
        defaults.put(RecruitmentEmailTemplateType.INTERVIEW_RESCHEDULED, new TemplateDefaults(
                "Interview rescheduled - {{jobTitle}}",
                "# Interview rescheduled\n\nHi {{candidateName}},\n\nYour interview for **{{jobTitle}}** has been rescheduled.\n\n- **Date:** {{interviewDate}}\n- **Time:** {{interviewTime}}\n- **Mode:** {{interviewMode}}\n- **Meeting link:** {{meetingLink}}\n- **Location:** {{location}}"));
        defaults.put(RecruitmentEmailTemplateType.OFFER, new TemplateDefaults(
                "Offer update - {{jobTitle}}",
                "# Congratulations\n\nHi {{candidateName}},\n\nWe are delighted to move forward with an offer for **{{jobTitle}}** at {{companyName}}. Our HR team will share the offer details with you directly."));
        defaults.put(RecruitmentEmailTemplateType.REJECTED, new TemplateDefaults(
                "Update on your application - {{jobTitle}}",
                "# Application update\n\nHi {{candidateName}},\n\nThank you for your interest in **{{jobTitle}}** and for the time you invested in the process. We will not be moving forward with your application on this occasion.\n\nWe wish you every success in your job search."));
        defaults.put(RecruitmentEmailTemplateType.WELCOME_EMPLOYEE, new TemplateDefaults(
                "Welcome to {{companyName}}",
                "# Welcome to the team\n\nHi {{candidateName}},\n\nWe are excited to welcome you to **{{companyName}}** as our new **{{jobTitle}}**. Your WorkNest account details will be sent separately."));
        return defaults;
    }

    private Map<String, String> buildVariables(
            CandidateApplication application,
            String companyName,
            String careersLink,
            Interview interview) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("candidateName", safe(application.getCandidate().getFullName()));
        values.put("jobTitle", safe(application.getJobPosition().getTitle()));
        values.put("companyName", safe(companyName));
        values.put("careersLink", safe(careersLink));
        values.put("interviewDate", interview == null ? "To be confirmed" : DATE_FORMAT.format(interview.getScheduledAt()));
        values.put("interviewTime", interview == null ? "To be confirmed" : TIME_FORMAT.format(interview.getScheduledAt()));
        values.put("interviewMode", interview == null ? "To be confirmed" : interview.getMode().name().replace('_', ' '));
        values.put("meetingLink", interview == null ? "-" : safe(interview.getMeetingLink()));
        values.put("location", interview == null ? "-" : safe(interview.getLocation()));
        return values;
    }

    private String replaceVariables(String template, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template == null ? "" : template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(variables.getOrDefault(matcher.group(1), "")));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String renderMarkdown(String markdown) {
        String[] lines = escape(markdown).split("\\R", -1);
        StringBuilder html = new StringBuilder();
        boolean inList = false;
        boolean inCode = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("```")) {
                if (inList) { html.append("</ul>"); inList = false; }
                html.append(inCode ? "</code></pre>" : "<pre style=\"padding:12px;background:#f1f5f9;border-radius:8px;overflow:auto\"><code>");
                inCode = !inCode;
                continue;
            }
            if (inCode) { html.append(rawLine).append('\n'); continue; }
            if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!inList) { html.append("<ul>"); inList = true; }
                html.append("<li>").append(renderInline(line.substring(2))).append("</li>");
                continue;
            }
            if (inList) { html.append("</ul>"); inList = false; }
            if (line.isBlank()) { continue; }
            if (line.matches("-{3,}")) { html.append("<hr/>"); continue; }
            if (line.startsWith("### ")) { html.append("<h3>").append(renderInline(line.substring(4))).append("</h3>"); }
            else if (line.startsWith("## ")) { html.append("<h2>").append(renderInline(line.substring(3))).append("</h2>"); }
            else if (line.startsWith("# ")) { html.append("<h1>").append(renderInline(line.substring(2))).append("</h1>"); }
            else if (line.startsWith("&gt; ")) { html.append("<blockquote style=\"border-left:3px solid #8b5cf6;padding-left:12px;color:#475569\">").append(renderInline(line.substring(5))).append("</blockquote>"); }
            else { html.append("<p>").append(renderInline(line)).append("</p>"); }
        }
        if (inList) html.append("</ul>");
        if (inCode) html.append("</code></pre>");
        return html.toString();
    }

    private String renderInline(String value) {
        String result = value.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>")
                .replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<em>$1</em>");
        Matcher matcher = LINK_PATTERN.matcher(result);
        StringBuffer linked = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(linked, Matcher.quoteReplacement("<a href=\"" + matcher.group(2) + "\">" + matcher.group(1) + "</a>"));
        }
        matcher.appendTail(linked);
        return linked.toString();
    }

    private String wrapEmail(String subject, String content, BrandContext brand) {
        return "<!doctype html><html><body style=\"margin:0;background:#f8fafc;font-family:Segoe UI,Arial,sans-serif;color:#0f172a\">"
                + "<div style=\"max-width:640px;margin:24px auto;padding:0 16px\"><div style=\"background:#fff;border:1px solid #e2e8f0;border-radius:16px;overflow:hidden\">"
                + "<div style=\"padding:22px 28px;background:" + escape(brand.primaryColor()) + ";color:" + emailForeground(brand.primaryColor()) + "\">"
                + "<strong>" + escape(brand.companyName()) + "</strong></div>"
                + "<div style=\"padding:28px;line-height:1.65\">" + content + "</div></div>"
                + "<p style=\"text-align:center;color:#64748b;font-size:12px\">Sent through WorkNest · " + escape(subject) + "</p></div></body></html>";
    }

    private BrandContext resolveBrand(String companyName) {
        BrandContext resolved = brandContextResolver == null
                ? BrandContext.workNest()
                : brandContextResolver.resolveCurrentTenantOrDefault();
        if (!"WorkNest".equals(resolved.companyName()) || companyName == null || companyName.isBlank()) {
            return resolved;
        }
        return new BrandContext(companyName.trim(), "#9332EA");
    }

    private String emailForeground(String hexColor) {
        if (hexColor == null || !hexColor.matches("^#[0-9A-Fa-f]{6}$")) return "#FFFFFF";
        int red = Integer.parseInt(hexColor.substring(1, 3), 16);
        int green = Integer.parseInt(hexColor.substring(3, 5), 16);
        int blue = Integer.parseInt(hexColor.substring(5, 7), 16);
        return (red * 299 + green * 587 + blue * 114) / 1000 > 150 ? "#111827" : "#FFFFFF";
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private RecruitmentEmailTemplateResponseDto toResponse(RecruitmentEmailTemplate template) {
        return RecruitmentEmailTemplateResponseDto.builder()
                .id(template.getId()).type(template.getType()).subject(template.getSubject())
                .bodyMarkdown(template.getBodyMarkdown()).enabled(template.isEnabled())
                .availableVariables(new ArrayList<>(VARIABLES)).updatedAt(template.getUpdatedAt()).build();
    }

    private RecruitmentEmailLogResponseDto toLogResponse(RecruitmentEmailLog log) {
        return RecruitmentEmailLogResponseDto.builder()
                .id(log.getId()).templateType(log.getTemplateType()).recipientEmail(log.getRecipientEmail())
                .subject(log.getSubject()).deliveryStatus(log.getDeliveryStatus()).sentAt(log.getSentAt()).build();
    }

    private record TemplateDefaults(String subject, String body) {}
}
