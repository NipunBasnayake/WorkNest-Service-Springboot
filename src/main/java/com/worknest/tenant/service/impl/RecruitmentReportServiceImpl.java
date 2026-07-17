package com.worknest.tenant.service.impl;

import com.worknest.common.exception.BadRequestException;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.report.RecruitmentReportPageDto;
import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.entity.Interview;
import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import com.worknest.tenant.enums.InterviewStatus;
import com.worknest.tenant.enums.JobPositionStatus;
import com.worknest.tenant.enums.RecruitmentReportType;
import com.worknest.tenant.repository.CandidateApplicationRepository;
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
    private final AuthorizationService authorizationService;

    public RecruitmentReportServiceImpl(
            JobPositionRepository jobPositionRepository,
            CandidateApplicationRepository candidateApplicationRepository,
            InterviewRepository interviewRepository,
            AuthorizationService authorizationService) {
        this.jobPositionRepository = jobPositionRepository;
        this.candidateApplicationRepository = candidateApplicationRepository;
        this.interviewRepository = interviewRepository;
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
                        "applied", application.getAppliedAt())
        ).toList();

        Map<String, Long> summary = new LinkedHashMap<>();
        if (hiringOnly) {
            summary.put("Hires", result.getTotalElements());
            summary.put("Employee accounts", candidateApplicationRepository.count(specification.and((root, query, cb) -> cb.isNotNull(root.get("hiredEmployee")))));
            summary.put("Awaiting account", candidateApplicationRepository.count(specification.and((root, query, cb) -> cb.isNull(root.get("hiredEmployee")))));
        } else {
            summary.put("Applications", result.getTotalElements());
            summary.put("Shortlisted", candidateApplicationRepository.count(specification.and(applicationStatuses(shortlistedStatuses()))));
            summary.put("Interviews", candidateApplicationRepository.count(specification.and(applicationStatuses(interviewStatuses()))));
            summary.put("Offers", candidateApplicationRepository.count(specification.and(applicationStatuses(List.of(CandidatePipelineStatus.OFFERED)))));
            summary.put("Hired", candidateApplicationRepository.count(specification.and(applicationStatuses(List.of(CandidatePipelineStatus.HIRED)))));
        }
        return response(result, rows, summary);
    }

    private RecruitmentReportPageDto interviewReport(ReportCriteria criteria, Pageable pageable) {
        Specification<Interview> specification = interviewSpecification(criteria);
        Page<Interview> result = interviewRepository.findAll(specification, pageable);
        List<Map<String, Object>> rows = result.getContent().stream().map(interview -> row(
                "candidate", interview.getApplication().getCandidate().getFullName(),
                "position", interview.getApplication().getJobPosition().getTitle(),
                "interviewer", employeeName(interview.getInterviewer()),
                "scheduled", interview.getScheduledAt(),
                "mode", interview.getMode().name(),
                "status", interview.getStatus().name()
        )).toList();

        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("Interviews", result.getTotalElements());
        summary.put("Scheduled", interviewRepository.count(specification.and(interviewStatus(EnumSet.of(InterviewStatus.SCHEDULED, InterviewStatus.RESCHEDULED)))));
        summary.put("Completed", interviewRepository.count(specification.and(interviewStatus(EnumSet.of(InterviewStatus.COMPLETED)))));
        summary.put("Cancelled", interviewRepository.count(specification.and(interviewStatus(EnumSet.of(InterviewStatus.CANCELLED)))));
        return response(result, rows, summary);
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
            addContainsAny(predicates, cb, criteria.search(), candidate.get("fullName"), candidate.get("email"), job.get("title"), job.get("department"));
            addContains(predicates, cb, candidate.get("fullName"), criteria.column("candidate"));
            addContains(predicates, cb, candidate.get("email"), criteria.column("email"));
            addContains(predicates, cb, job.get("title"), criteria.column("position"));
            addContains(predicates, cb, job.get("department"), firstNonBlank(criteria.department(), criteria.column("department")));
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
            addContains(predicates, cb, job.get("department"), criteria.department());
            addDateRange(predicates, cb, root.get("scheduledAt"), criteria.fromDate(), criteria.toDate());
            String selectedStatus = firstNonBlank(criteria.status(), criteria.column("status"));
            if (selectedStatus != null) predicates.add(cb.equal(root.get("status"), parseEnum(InterviewStatus.class, selectedStatus, "status")));
            String mode = criteria.column("mode");
            if (mode != null) predicates.add(cb.equal(cb.lower(root.get("mode").as(String.class)), mode.toLowerCase(Locale.ROOT)));
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
        String property = switch (type) {
            case JOB_OPENINGS -> Map.of("job", "title", "department", "department", "status", "status", "publishing", "published", "deadline", "expiresAt", "openings", "openings")
                    .getOrDefault(sortBy, "createdAt");
            case APPLICATIONS -> Map.of("candidate", "candidate.fullName", "email", "candidate.email", "position", "jobPosition.title", "department", "jobPosition.department", "stage", "status", "applied", "appliedAt")
                    .getOrDefault(sortBy, "appliedAt");
            case INTERVIEWS -> Map.of("candidate", "application.candidate.fullName", "position", "application.jobPosition.title", "interviewer", "interviewer.firstName", "scheduled", "scheduledAt", "mode", "mode", "status", "status")
                    .getOrDefault(sortBy, "scheduledAt");
            case HIRING -> Map.of("candidate", "candidate.fullName", "email", "candidate.email", "position", "jobPosition.title", "department", "jobPosition.department", "hiredAt", "hiredAt", "employeeId", "hiredEmployee.employeeCode")
                    .getOrDefault(sortBy, "hiredAt");
        };
        return Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, property);
    }

    private <T> RecruitmentReportPageDto response(Page<T> page, List<Map<String, Object>> rows, Map<String, Long> summary) {
        return RecruitmentReportPageDto.builder()
                .rows(rows)
                .summary(summary)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .generatedAt(LocalDateTime.now())
                .build();
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
