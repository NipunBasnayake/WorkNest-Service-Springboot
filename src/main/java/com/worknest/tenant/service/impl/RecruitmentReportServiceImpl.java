package com.worknest.tenant.service.impl;

import com.worknest.common.exception.BadRequestException;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.report.RecruitmentReportPageDto;
import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.entity.Interview;
import com.worknest.tenant.entity.InterviewFeedback;
import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import com.worknest.tenant.enums.InterviewRecommendation;
import com.worknest.tenant.enums.InterviewStatus;
import com.worknest.tenant.enums.JobPositionStatus;
import com.worknest.tenant.enums.RecruitmentReportType;
import com.worknest.tenant.repository.CandidateApplicationRepository;
import com.worknest.tenant.repository.InterviewFeedbackRepository;
import com.worknest.tenant.repository.InterviewRepository;
import com.worknest.tenant.repository.JobPositionRepository;
import com.worknest.tenant.service.RecruitmentReportService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(transactionManager = "transactionManager", readOnly = true)
public class RecruitmentReportServiceImpl implements RecruitmentReportService {

    private static final int MAX_PAGE_SIZE = 200;

    private final JobPositionRepository jobPositionRepository;
    private final CandidateApplicationRepository candidateApplicationRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final AuthorizationService authorizationService;

    public RecruitmentReportServiceImpl(
            JobPositionRepository jobPositionRepository,
            CandidateApplicationRepository candidateApplicationRepository,
            InterviewRepository interviewRepository,
            InterviewFeedbackRepository interviewFeedbackRepository,
            AuthorizationService authorizationService) {
        this.jobPositionRepository = jobPositionRepository;
        this.candidateApplicationRepository = candidateApplicationRepository;
        this.interviewRepository = interviewRepository;
        this.interviewFeedbackRepository = interviewFeedbackRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public RecruitmentReportPageDto getReport(
            RecruitmentReportType type,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            String department,
            Map<String, String> columnFilters,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        authorizationService.requirePermission(Permission.VIEW_RECRUITMENT);
        validateDateRange(fromDate, toDate);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, buildSort(type, sortBy, sortDir));
        ReportCriteria criteria = new ReportCriteria(
                trimToNull(search),
                fromDate,
                toDate,
                trimToNull(status),
                trimToNull(department),
                sanitizeColumnFilters(columnFilters));

        return switch (type) {
            case JOB_OPENINGS -> jobReport(criteria, pageable);
            case APPLICATIONS -> applicationReport(criteria, pageable, false);
            case INTERVIEWS -> interviewReport(criteria, pageable);
            case HIRING -> applicationReport(criteria, pageable, true);
        };
    }

    private RecruitmentReportPageDto jobReport(ReportCriteria criteria, Pageable pageable) {
        Specification<JobPosition> specification = jobSpecification(criteria);
        Page<JobPosition> result = jobPositionRepository.findAll(specification, pageable);
        List<Long> jobIds = result.getContent().stream().map(JobPosition::getId).toList();
        Map<Long, Long> applicationCounts = jobIds.isEmpty()
                ? Map.of()
                : candidateApplicationRepository.countByJobPositionIds(jobIds).stream()
                        .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> ((Number) row[1]).longValue()));

        List<Map<String, Object>> rows = result.getContent().stream().map(job -> row(
                "job", job.getTitle(),
                "department", fallback(job.getDepartment()),
                "status", job.getStatus().name(),
                "publishing", job.isPublished() ? "PUBLISHED" : "UNPUBLISHED",
                "deadline", job.getExpiresAt(),
                "openings", job.getOpenings(),
                "applicants", applicationCounts.getOrDefault(job.getId(), 0L)
        )).toList();

        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("Job openings", result.getTotalElements());
        summary.put("Open", jobPositionRepository.count(specification.and(jobStatus(JobPositionStatus.OPEN))));
        summary.put("Published", jobPositionRepository.count(specification.and((root, query, cb) -> cb.isTrue(root.get("published")))));
        summary.put("Closed", jobPositionRepository.count(specification.and(jobStatus(JobPositionStatus.CLOSED))));
        return response(result, rows, summary);
    }

    private RecruitmentReportPageDto applicationReport(ReportCriteria criteria, Pageable pageable, boolean hiringOnly) {
        Specification<CandidateApplication> specification = applicationSpecification(criteria, hiringOnly);
        Page<CandidateApplication> result = candidateApplicationRepository.findAll(specification, pageable);
        List<Map<String, Object>> rows = result.getContent().stream().map(application -> hiringOnly
                ? row(
                        "candidate", application.getCandidate().getFullName(),
                        "email", application.getCandidate().getEmail(),
                        "position", application.getJobPosition().getTitle(),
                        "department", fallback(application.getJobPosition().getDepartment()),
                        "hiredAt", application.getHiredAt(),
                        "employeeId", application.getHiredEmployee() == null ? "—" : application.getHiredEmployee().getEmployeeCode())
                : row(
                        "candidate", application.getCandidate().getFullName(),
                        "email", application.getCandidate().getEmail(),
                        "position", application.getJobPosition().getTitle(),
                        "department", fallback(application.getJobPosition().getDepartment()),
                        "stage", canonicalStage(application.getStatus()).name(),
                        "source", fallback(firstNonBlank(application.getSource(), application.getCandidate().getSource())),
                        "applied", application.getAppliedAt())
        ).toList();

        Map<String, Long> summary = new LinkedHashMap<>();
        if (hiringOnly) {
            summary.put("Hires", result.getTotalElements());
            summary.put("Employee accounts", candidateApplicationRepository.count(specification.and((root, query, cb) -> cb.isNotNull(root.get("hiredEmployee")))));
            summary.put("Awaiting account", candidateApplicationRepository.count(specification.and((root, query, cb) -> cb.isNull(root.get("hiredEmployee")))));
        } else {
            summary.put("Applications", result.getTotalElements());
            summary.put("Applied", candidateApplicationRepository.count(specification.and(applicationStatuses(List.of(CandidatePipelineStatus.APPLIED)))));
            summary.put("Shortlisted", candidateApplicationRepository.count(specification.and(applicationStatuses(shortlistedStatuses()))));
            summary.put("Interviews", candidateApplicationRepository.count(specification.and(applicationStatuses(interviewStatuses()))));
            summary.put("Offers", candidateApplicationRepository.count(specification.and(applicationStatuses(List.of(CandidatePipelineStatus.OFFERED)))));
            summary.put("Hired", candidateApplicationRepository.count(specification.and(applicationStatuses(List.of(CandidatePipelineStatus.HIRED)))));
            summary.put("Rejected", candidateApplicationRepository.count(specification.and(applicationStatuses(List.of(CandidatePipelineStatus.REJECTED, CandidatePipelineStatus.WITHDRAWN)))));
        }
        return response(result, rows, summary);
    }

    private RecruitmentReportPageDto interviewReport(ReportCriteria criteria, Pageable pageable) {
        Specification<Interview> specification = interviewSpecification(criteria);
        Page<Interview> result = interviewRepository.findAll(specification, pageable);
        List<Long> pageInterviewIds = result.getContent().stream().map(Interview::getId).toList();
        Map<Long, InterviewFeedback> pageFeedback = pageInterviewIds.isEmpty()
                ? Map.of()
                : interviewFeedbackRepository.findByInterviewIdIn(pageInterviewIds).stream()
                        .collect(Collectors.toMap(feedback -> feedback.getInterview().getId(), feedback -> feedback));
        List<Map<String, Object>> rows = result.getContent().stream().map(interview -> row(
                "interviewId", interview.getId(),
                "candidate", interview.getApplication().getCandidate().getFullName(),
                "position", interview.getApplication().getJobPosition().getTitle(),
                "department", fallback(interview.getApplication().getJobPosition().getDepartment()),
                "employeeId", interview.getInterviewer().getId(),
                "interviewer", employeeName(interview.getInterviewer()),
                "scheduled", interview.getScheduledAt(),
                "mode", interview.getMode().name(),
                "status", interview.getStatus().name(),
                "outcome", recommendationName(pageFeedback.get(interview.getId()))
        )).toList();

        List<Interview> filteredInterviews = interviewRepository.findAll(specification);
        List<Long> filteredInterviewIds = filteredInterviews.stream().map(Interview::getId).toList();
        Map<Long, InterviewFeedback> filteredFeedback = filteredInterviewIds.isEmpty()
                ? Map.of()
                : interviewFeedbackRepository.findByInterviewIdIn(filteredInterviewIds).stream()
                        .collect(Collectors.toMap(feedback -> feedback.getInterview().getId(), feedback -> feedback));
        long recordedOutcomes = filteredFeedback.values().stream()
                .filter(feedback -> feedback.getRecommendation() != null)
                .count();
        long successfulOutcomes = filteredFeedback.values().stream()
                .filter(feedback -> feedback.getRecommendation() == InterviewRecommendation.HIRE
                        || feedback.getRecommendation() == InterviewRecommendation.STRONG_HIRE)
                .count();

        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("Interviews", result.getTotalElements());
        summary.put("Scheduled", interviewRepository.count(specification.and(interviewStatus(EnumSet.of(InterviewStatus.SCHEDULED)))));
        summary.put("Completed", interviewRepository.count(specification.and(interviewStatus(EnumSet.of(InterviewStatus.COMPLETED)))));
        summary.put("Recorded outcomes", recordedOutcomes);
        summary.put("Successful outcomes", successfulOutcomes);
        return response(result, rows, summary, interviewCharts(filteredInterviews, filteredFeedback));
    }

    private Specification<JobPosition> jobSpecification(ReportCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            addContainsAny(predicates, cb, criteria.search(), root.get("title"), root.get("department"), root.get("location"));
            addContains(predicates, cb, root.get("title"), criteria.column("job"));
            addContains(predicates, cb, root.get("department"), firstNonBlank(criteria.department(), criteria.column("department")));
            addDateRange(predicates, cb, root.get("createdAt"), criteria.fromDate(), criteria.toDate());
            String selectedStatus = firstNonBlank(criteria.status(), criteria.column("status"));
            if (selectedStatus != null) predicates.add(cb.equal(root.get("status"), parseEnum(JobPositionStatus.class, selectedStatus, "status")));
            String publishing = criteria.column("publishing");
            if (publishing != null) predicates.add(cb.equal(root.get("published"), publishing.equalsIgnoreCase("PUBLISHED")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<CandidateApplication> applicationSpecification(ReportCriteria criteria, boolean hiringOnly) {
        return (root, query, cb) -> {
            var candidate = root.join("candidate");
            var job = root.join("jobPosition");
            List<Predicate> predicates = new ArrayList<>();
            if (hiringOnly) predicates.add(cb.equal(root.get("status"), CandidatePipelineStatus.HIRED));
            addContainsAny(predicates, cb, criteria.search(), candidate.get("fullName"), candidate.get("email"), job.get("title"), job.get("department"), root.get("source"), candidate.get("source"));
            addContains(predicates, cb, candidate.get("fullName"), criteria.column("candidate"));
            addContains(predicates, cb, candidate.get("email"), criteria.column("email"));
            addContains(predicates, cb, job.get("title"), criteria.column("position"));
            addContains(predicates, cb, job.get("department"), firstNonBlank(criteria.department(), criteria.column("department")));
            addContainsAny(predicates, cb, criteria.column("source"), root.get("source"), candidate.get("source"));
            addDateRange(predicates, cb, root.get(hiringOnly ? "hiredAt" : "appliedAt"), criteria.fromDate(), criteria.toDate());
            if (!hiringOnly) {
                String selectedStatus = firstNonBlank(criteria.status(), criteria.column("stage"));
                if (selectedStatus != null) predicates.add(root.get("status").in(stageStatuses(selectedStatus)));
            }
            if (hiringOnly) addContains(predicates, cb, root.join("hiredEmployee", JoinType.LEFT).get("employeeCode"), criteria.column("employeeId"));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<Interview> interviewSpecification(ReportCriteria criteria) {
        return (root, query, cb) -> {
            var application = root.join("application");
            var candidate = application.join("candidate");
            var job = application.join("jobPosition");
            var interviewer = root.join("interviewer");
            List<Predicate> predicates = new ArrayList<>();
            addContainsAny(predicates, cb, criteria.search(), candidate.get("fullName"), job.get("title"), interviewer.get("firstName"), interviewer.get("lastName"));
            addContains(predicates, cb, candidate.get("fullName"), criteria.column("candidate"));
            addContains(predicates, cb, job.get("title"), criteria.column("position"));
            addContainsAny(predicates, cb, criteria.column("interviewer"), interviewer.get("firstName"), interviewer.get("lastName"));
            addContains(predicates, cb, job.get("department"), firstNonBlank(criteria.department(), criteria.column("department")));
            addDateRange(predicates, cb, root.get("scheduledAt"), criteria.fromDate(), criteria.toDate());
            String employeeId = criteria.column("employeeId");
            if (employeeId != null) {
                try {
                    predicates.add(cb.equal(interviewer.get("id"), Long.valueOf(employeeId)));
                } catch (NumberFormatException exception) {
                    throw new BadRequestException("employeeId has an unsupported value");
                }
            }
            String selectedStatus = firstNonBlank(criteria.status(), criteria.column("status"));
            if (selectedStatus != null) predicates.add(cb.equal(root.get("status"), parseEnum(InterviewStatus.class, selectedStatus, "status")));
            String mode = criteria.column("mode");
            if (mode != null) predicates.add(cb.equal(cb.lower(root.get("mode").as(String.class)), mode.toLowerCase(Locale.ROOT)));
            String outcome = criteria.column("outcome");
            if (outcome != null) {
                InterviewRecommendation recommendation = parseEnum(InterviewRecommendation.class, outcome, "outcome");
                var feedbackQuery = query.subquery(Long.class);
                var feedback = feedbackQuery.from(InterviewFeedback.class);
                feedbackQuery.select(feedback.get("interview").get("id"))
                        .where(
                                cb.equal(feedback.get("interview").get("id"), root.get("id")),
                                cb.equal(feedback.get("recommendation"), recommendation));
                predicates.add(cb.exists(feedbackQuery));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<JobPosition> jobStatus(JobPositionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private Specification<CandidateApplication> applicationStatuses(Collection<CandidatePipelineStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    private Specification<Interview> interviewStatus(Collection<InterviewStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    private Sort buildSort(RecruitmentReportType type, String sortBy, String sortDir) {
        boolean descending = !"asc".equalsIgnoreCase(sortDir);
        // Map.of rejects null lookup keys. Most report requests intentionally
        // omit sortBy, so normalize that normal case before selecting a safe
        // server-controlled property.
        String requestedSort = trimToNull(sortBy);
        if (requestedSort == null) requestedSort = "";
        String property = switch (type) {
            case JOB_OPENINGS -> Map.of("job", "title", "department", "department", "status", "status", "publishing", "published", "deadline", "expiresAt", "openings", "openings")
                    .getOrDefault(requestedSort, "createdAt");
            case APPLICATIONS -> Map.of("candidate", "candidate.fullName", "email", "candidate.email", "position", "jobPosition.title", "department", "jobPosition.department", "stage", "status", "applied", "appliedAt")
                    .getOrDefault(requestedSort, "appliedAt");
            case INTERVIEWS -> Map.of("candidate", "application.candidate.fullName", "position", "application.jobPosition.title", "interviewer", "interviewer.firstName", "scheduled", "scheduledAt", "mode", "mode", "status", "status")
                    .getOrDefault(requestedSort, "scheduledAt");
            case HIRING -> Map.of("candidate", "candidate.fullName", "email", "candidate.email", "position", "jobPosition.title", "department", "jobPosition.department", "hiredAt", "hiredAt", "employeeId", "hiredEmployee.employeeCode")
                    .getOrDefault(requestedSort, "hiredAt");
        };
        return Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, property);
    }

    private <T> RecruitmentReportPageDto response(Page<T> page, List<Map<String, Object>> rows, Map<String, Long> summary) {
        return response(page, rows, summary, List.of());
    }

    private <T> RecruitmentReportPageDto response(
            Page<T> page,
            List<Map<String, Object>> rows,
            Map<String, Long> summary,
            List<Map<String, Object>> supportingCharts) {
        return RecruitmentReportPageDto.builder()
                .rows(rows)
                .summary(summary)
                .supportingCharts(supportingCharts)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private List<Map<String, Object>> interviewCharts(
            List<Interview> interviews,
            Map<Long, InterviewFeedback> feedbackByInterviewId) {
        Map<String, Long> statuses = new LinkedHashMap<>();
        for (InterviewStatus status : InterviewStatus.values()) statuses.put(status.name(), 0L);
        Map<String, Long> modes = new LinkedHashMap<>();
        Map<String, Long> dailySchedule = new java.util.TreeMap<>();
        Map<String, Long> monthlyInterviews = new java.util.TreeMap<>();
        Map<String, Long> interviewers = new java.util.HashMap<>();
        Map<String, Long> outcomes = new LinkedHashMap<>();
        for (InterviewRecommendation recommendation : InterviewRecommendation.values()) {
            outcomes.put(recommendation.name(), 0L);
        }

        for (Interview interview : interviews) {
            statuses.computeIfPresent(interview.getStatus().name(), (key, count) -> count + 1);
            modes.merge(interview.getMode().name(), 1L, Long::sum);
            dailySchedule.merge(interview.getScheduledAt().toLocalDate().toString(), 1L, Long::sum);
            monthlyInterviews.merge(
                    interview.getScheduledAt().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                    1L,
                    Long::sum);
            interviewers.merge(employeeName(interview.getInterviewer()), 1L, Long::sum);
            InterviewFeedback feedback = feedbackByInterviewId.get(interview.getId());
            if (feedback != null && feedback.getRecommendation() != null) {
                outcomes.computeIfPresent(feedback.getRecommendation().name(), (key, count) -> count + 1);
            }
        }

        List<Map<String, Object>> charts = new ArrayList<>();
        charts.add(reportChart("Interview Status", "Interviews by exact database status", "donut", statuses));
        charts.add(reportChart("Interview Type", "Interviews by recorded mode", "bar", modes));
        charts.add(reportChart("Interview Schedule", "Scheduled interview records by day", "line", dailySchedule));
        charts.add(reportChart("Interview Outcome", "Recorded interview feedback recommendations", "bar", outcomes));
        charts.add(reportChart("Interviewer Distribution", "Interviews assigned to each interviewer", "horizontalBar",
                interviewers.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new))));
        charts.add(reportChart("Monthly Interviews", "Interview records by scheduled month", "line", monthlyInterviews));
        return charts;
    }

    private Map<String, Object> reportChart(
            String title,
            String subtitle,
            String variant,
            Map<String, Long> values) {
        List<Map<String, Object>> data = values.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> row("label", entry.getKey(), "value", entry.getValue()))
                .toList();
        return row("title", title, "subtitle", subtitle, "variant", variant, "data", data);
    }

    private String recommendationName(InterviewFeedback feedback) {
        return feedback == null || feedback.getRecommendation() == null
                ? null
                : feedback.getRecommendation().name();
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) row.put(String.valueOf(values[index]), values[index + 1]);
        return row;
    }

    @SafeVarargs
    private final void addContainsAny(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb, String value, jakarta.persistence.criteria.Expression<String>... fields) {
        String normalized = trimToNull(value);
        if (normalized == null) return;
        String pattern = "%" + normalized.toLowerCase(Locale.ROOT) + "%";
        Predicate[] options = java.util.Arrays.stream(fields).map(field -> cb.like(cb.lower(cb.coalesce(field, "")), pattern)).toArray(Predicate[]::new);
        predicates.add(cb.or(options));
    }

    private void addContains(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb, jakarta.persistence.criteria.Expression<String> field, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) predicates.add(cb.like(cb.lower(cb.coalesce(field, "")), "%" + normalized.toLowerCase(Locale.ROOT) + "%"));
    }

    private void addDateRange(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb, jakarta.persistence.criteria.Path<LocalDateTime> field, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null) predicates.add(cb.greaterThanOrEqualTo(field, fromDate.atStartOfDay()));
        if (toDate != null) predicates.add(cb.lessThan(field, toDate.plusDays(1).atStartOfDay()));
    }

    private List<CandidatePipelineStatus> stageStatuses(String value) {
        CandidatePipelineStatus status = parseEnum(CandidatePipelineStatus.class, value, "status");
        if (status == CandidatePipelineStatus.SHORTLISTED) return shortlistedStatuses();
        if (status == CandidatePipelineStatus.INTERVIEW) return interviewStatuses();
        if (status == CandidatePipelineStatus.REJECTED) return List.of(CandidatePipelineStatus.REJECTED, CandidatePipelineStatus.WITHDRAWN);
        return List.of(status);
    }

    private List<CandidatePipelineStatus> shortlistedStatuses() {
        return List.of(CandidatePipelineStatus.SHORTLISTED, CandidatePipelineStatus.SCREENING);
    }

    private List<CandidatePipelineStatus> interviewStatuses() {
        return List.of(CandidatePipelineStatus.INTERVIEW, CandidatePipelineStatus.TECHNICAL, CandidatePipelineStatus.HR_REVIEW);
    }

    private CandidatePipelineStatus canonicalStage(CandidatePipelineStatus status) {
        if (status == CandidatePipelineStatus.SCREENING) return CandidatePipelineStatus.SHORTLISTED;
        if (status == CandidatePipelineStatus.TECHNICAL || status == CandidatePipelineStatus.HR_REVIEW) return CandidatePipelineStatus.INTERVIEW;
        if (status == CandidatePipelineStatus.WITHDRAWN) return CandidatePipelineStatus.REJECTED;
        return status;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String fieldName) {
        try {
            return Enum.valueOf(type, value.trim().replace(' ', '_').toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BadRequestException(fieldName + " has an unsupported value");
        }
    }

    private Map<String, String> sanitizeColumnFilters(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Map.of();
        return values.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().startsWith("column."))
                .map(entry -> new java.util.AbstractMap.SimpleImmutableEntry<>(
                        entry.getKey().substring("column.".length()),
                        trimToNull(entry.getValue())))
                .filter(entry -> entry.getValue() != null && !entry.getKey().isBlank())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right));
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) throw new BadRequestException("toDate cannot be before fromDate");
    }

    private String fallback(String value) {
        return trimToNull(value) == null ? "—" : value.trim();
    }

    private String employeeName(com.worknest.tenant.entity.Employee employee) {
        String firstName = trimToNull(employee.getFirstName());
        String lastName = trimToNull(employee.getLastName());
        return java.util.stream.Stream.of(firstName, lastName)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    private String firstNonBlank(String first, String second) {
        return trimToNull(first) != null ? trimToNull(first) : trimToNull(second);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ReportCriteria(
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            String department,
            Map<String, String> columnFilters) {
        private String column(String key) {
            return columnFilters.get(key);
        }
    }
}
