package com.worknest.tenant.service.impl;

import com.worknest.common.exception.BadRequestException;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.report.NotificationReportPageDto;
import com.worknest.tenant.entity.Notification;
import com.worknest.tenant.entity.ProjectTeam;
import com.worknest.tenant.entity.Task;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.repository.NotificationRepository;
import com.worknest.tenant.service.NotificationReportService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@Transactional(transactionManager = "transactionManager", readOnly = true)
public class NotificationReportServiceImpl implements NotificationReportService {

    private static final int MAX_PAGE_SIZE = 200;

    private final NotificationRepository notificationRepository;
    private final AuthorizationService authorizationService;

    public NotificationReportServiceImpl(
            NotificationRepository notificationRepository,
            AuthorizationService authorizationService) {
        this.notificationRepository = notificationRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public NotificationReportPageDto getReport(
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            String department,
            Long employeeId,
            Long projectId,
            Long teamId,
            Map<String, String> columnFilters,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        authorizationService.requirePermission(Permission.MANAGE_TENANT_SETTINGS);
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BadRequestException("toDate cannot be before fromDate");
        }

        ReportCriteria criteria = new ReportCriteria(
                trimToNull(search),
                fromDate,
                toDate,
                trimToNull(status),
                trimToNull(department),
                employeeId,
                projectId,
                teamId,
                sanitizeColumnFilters(columnFilters));
        Specification<Notification> specification = notificationSpecification(criteria);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort sort = reportSort(sortBy, sortDir);
        Page<Notification> result = notificationRepository.findAll(
                specification,
                PageRequest.of(safePage, safeSize, sort));
        List<Notification> filteredNotifications = notificationRepository.findAll(specification, sort);

        List<Map<String, Object>> rows = result.getContent().stream()
                .map(notification -> row(
                        "notificationId", notification.getId(),
                        "employeeId", notification.getRecipient().getId(),
                        "recipient", employeeName(notification),
                        "department", notification.getRecipient().getDepartment(),
                        "message", notification.getMessage(),
                        "type", notification.getType().name(),
                        "category", notification.getRelatedEntityType(),
                        "relatedEntityId", notification.getRelatedEntityId(),
                        "status", notification.isRead() ? "READ" : "UNREAD",
                        "created", notification.getCreatedAt()))
                .toList();

        long readCount = filteredNotifications.stream().filter(Notification::isRead).count();
        long unreadCount = filteredNotifications.size() - readCount;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("Notifications", (long) filteredNotifications.size());
        summary.put("Read", readCount);
        summary.put("Unread", unreadCount);
        summary.put("Read %", percentage(readCount, filteredNotifications.size()));
        summary.put("Unread %", percentage(unreadCount, filteredNotifications.size()));

        return NotificationReportPageDto.builder()
                .rows(rows)
                .summary(summary)
                .supportingCharts(notificationCharts(filteredNotifications))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private Specification<Notification> notificationSpecification(ReportCriteria criteria) {
        return (root, query, cb) -> {
            var recipient = root.join("recipient");
            List<Predicate> predicates = new ArrayList<>();
            String search = criteria.search();
            if (search != null) {
                String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("message")), pattern),
                        cb.like(cb.lower(root.get("type").as(String.class)), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("relatedEntityType"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(recipient.get("firstName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(recipient.get("lastName"), "")), pattern)));
            }
            if (criteria.fromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.fromDate().atStartOfDay()));
            }
            if (criteria.toDate() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), criteria.toDate().plusDays(1).atStartOfDay()));
            }
            String department = firstNonBlank(criteria.department(), criteria.column("department"));
            if (department != null) {
                predicates.add(cb.like(
                        cb.lower(cb.coalesce(recipient.get("department"), "")),
                        "%" + department.toLowerCase(Locale.ROOT) + "%"));
            }
            if (criteria.employeeId() != null) {
                predicates.add(cb.equal(recipient.get("id"), criteria.employeeId()));
            }
            String selectedStatus = firstNonBlank(criteria.status(), criteria.column("status"));
            if (selectedStatus != null) {
                if ("READ".equalsIgnoreCase(selectedStatus)) predicates.add(cb.isTrue(root.get("read")));
                else if ("UNREAD".equalsIgnoreCase(selectedStatus)) predicates.add(cb.isFalse(root.get("read")));
                else throw new BadRequestException("status has an unsupported value");
            }
            String type = criteria.column("type");
            if (type != null) {
                predicates.add(cb.like(
                        cb.lower(root.get("type").as(String.class)),
                        "%" + type.toLowerCase(Locale.ROOT) + "%"));
            }
            String category = criteria.column("category");
            if (category != null) {
                predicates.add(cb.like(
                        cb.lower(cb.coalesce(root.get("relatedEntityType"), "")),
                        "%" + category.toLowerCase(Locale.ROOT) + "%"));
            }
            String recipientName = criteria.column("recipient");
            if (recipientName != null) {
                String pattern = "%" + recipientName.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(recipient.get("firstName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(recipient.get("lastName"), "")), pattern)));
            }
            String message = criteria.column("message");
            if (message != null) {
                predicates.add(cb.like(
                        cb.lower(root.get("message")),
                        "%" + message.toLowerCase(Locale.ROOT) + "%"));
            }
            String relatedEntityId = criteria.column("relatedEntityId");
            if (relatedEntityId != null) {
                try {
                    predicates.add(cb.equal(root.get("relatedEntityId"), Long.valueOf(relatedEntityId)));
                } catch (NumberFormatException exception) {
                    throw new BadRequestException("relatedEntityId has an unsupported value");
                }
            }
            if (criteria.projectId() != null) {
                var taskQuery = query.subquery(Long.class);
                var task = taskQuery.from(Task.class);
                taskQuery.select(task.get("id")).where(
                        cb.equal(task.get("id"), root.get("relatedEntityId")),
                        cb.equal(task.get("project").get("id"), criteria.projectId()));
                predicates.add(cb.or(
                        cb.and(
                                cb.equal(cb.upper(root.get("relatedEntityType")), "PROJECT"),
                                cb.equal(root.get("relatedEntityId"), criteria.projectId())),
                        cb.and(
                                cb.equal(cb.upper(root.get("relatedEntityType")), "TASK"),
                                cb.exists(taskQuery))));
            }
            if (criteria.teamId() != null) {
                var taskQuery = query.subquery(Long.class);
                var task = taskQuery.from(Task.class);
                taskQuery.select(task.get("id")).where(
                        cb.equal(task.get("id"), root.get("relatedEntityId")),
                        cb.equal(task.get("assignedTeam").get("id"), criteria.teamId()));
                var projectQuery = query.subquery(Long.class);
                var projectTeam = projectQuery.from(ProjectTeam.class);
                projectQuery.select(projectTeam.get("project").get("id")).where(
                        cb.equal(projectTeam.get("project").get("id"), root.get("relatedEntityId")),
                        cb.equal(projectTeam.get("team").get("id"), criteria.teamId()));
                predicates.add(cb.or(
                        cb.and(
                                cb.equal(cb.upper(root.get("relatedEntityType")), "TASK"),
                                cb.exists(taskQuery)),
                        cb.and(
                                cb.equal(cb.upper(root.get("relatedEntityType")), "PROJECT"),
                                cb.exists(projectQuery))));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<Map<String, Object>> notificationCharts(List<Notification> notifications) {
        Map<String, Long> categories = new LinkedHashMap<>();
        Map<String, Long> types = new LinkedHashMap<>();
        for (NotificationType type : NotificationType.values()) types.put(type.name(), 0L);
        Map<String, Long> readStatus = new LinkedHashMap<>();
        readStatus.put("READ", 0L);
        readStatus.put("UNREAD", 0L);
        Map<String, Long> daily = new TreeMap<>();

        for (Notification notification : notifications) {
            String category = trimToNull(notification.getRelatedEntityType());
            if (category != null) categories.merge(category, 1L, Long::sum);
            types.computeIfPresent(notification.getType().name(), (key, count) -> count + 1);
            readStatus.computeIfPresent(notification.isRead() ? "READ" : "UNREAD", (key, count) -> count + 1);
            daily.merge(notification.getCreatedAt().toLocalDate().toString(), 1L, Long::sum);
        }

        Map<String, Long> rankedCategories = categories.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
        return List.of(
                reportChart("Notifications by Category", "Stored related-entity categories", "donut", categories),
                reportChart("Notifications by Type", "Exact notification type values", "bar", types),
                reportChart("Notifications by Read Status", "Read and unread records", "donut", readStatus),
                reportChart("Daily Notifications", "Notification records created each day", "line", daily),
                reportChart("Top Categories", "Categories ranked by notification count", "horizontalBar", rankedCategories));
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

    private Sort reportSort(String sortBy, String sortDir) {
        String property = switch (trimToNull(sortBy) == null ? "" : sortBy) {
            case "recipient" -> "recipient.firstName";
            case "department" -> "recipient.department";
            case "message" -> "message";
            case "type" -> "type";
            case "category" -> "relatedEntityType";
            case "relatedEntityId" -> "relatedEntityId";
            case "status" -> "read";
            default -> "createdAt";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
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

    private String employeeName(Notification notification) {
        String first = trimToNull(notification.getRecipient().getFirstName());
        String last = trimToNull(notification.getRecipient().getLastName());
        return java.util.stream.Stream.of(first, last)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    private String percentage(long value, long total) {
        if (total == 0) return "No data available";
        double percentage = value * 100.0 / total;
        return String.format(Locale.ROOT, "%.1f%%", percentage);
    }

    private String firstNonBlank(String first, String second) {
        return trimToNull(first) != null ? trimToNull(first) : trimToNull(second);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }

    private record ReportCriteria(
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            String department,
            Long employeeId,
            Long projectId,
            Long teamId,
            Map<String, String> columnFilters) {
        private String column(String key) {
            return columnFilters.get(key);
        }
    }
}
