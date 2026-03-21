package com.worknest.tenant.service.impl;

import com.worknest.notification.email.EmailNotificationService;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.LeaveRequest;
import com.worknest.tenant.entity.Task;
import com.worknest.tenant.enums.LeaveStatus;
import com.worknest.tenant.enums.TaskStatus;
import com.worknest.tenant.repository.LeaveRequestRepository;
import com.worknest.tenant.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class TenantReminderProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(TenantReminderProcessorService.class);

    private final LeaveRequestRepository leaveRequestRepository;
    private final TaskRepository taskRepository;
    private final EmailNotificationService emailNotificationService;
    private final Set<Long> leaveReminderDays;
    private final Set<Long> taskReminderDays;

    public TenantReminderProcessorService(
            LeaveRequestRepository leaveRequestRepository,
            TaskRepository taskRepository,
            EmailNotificationService emailNotificationService,
            @Value("${app.reminders.leave.days-before-start:1,2}") String leaveReminderDaysRaw,
            @Value("${app.reminders.task.days-before-due:1,2,3}") String taskReminderDaysRaw) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.taskRepository = taskRepository;
        this.emailNotificationService = emailNotificationService;
        this.leaveReminderDays = parseDayConfig(leaveReminderDaysRaw);
        this.taskReminderDays = parseDayConfig(taskReminderDaysRaw);
    }

    @Transactional(transactionManager = "transactionManager")
    public void processTenantReminders(String tenantKey) {
        LocalDate today = LocalDate.now();
        processLeaveReminders(tenantKey, today);
        processTaskReminders(tenantKey, today);
    }

    private void processLeaveReminders(String tenantKey, LocalDate today) {
        if (leaveReminderDays.isEmpty()) {
            return;
        }

        LocalDate fromDate = today.plusDays(Collections.min(leaveReminderDays));
        LocalDate toDate = today.plusDays(Collections.max(leaveReminderDays));

        List<LeaveRequest> candidates = leaveRequestRepository.findUpcomingLeavesForReminder(
                LeaveStatus.APPROVED,
                fromDate,
                toDate
        );

        if (candidates.isEmpty()) {
            return;
        }

        List<LeaveRequest> updated = new ArrayList<>();
        for (LeaveRequest leaveRequest : candidates) {
            long daysUntil = ChronoUnit.DAYS.between(today, leaveRequest.getStartDate());
            if (!leaveReminderDays.contains(daysUntil)) {
                continue;
            }
            Employee employee = leaveRequest.getEmployee();
            try {
                emailNotificationService.sendLeaveReminderEmailOrThrow(
                        employee.getEmail(),
                        buildFullName(employee),
                        leaveRequest.getStartDate(),
                        leaveRequest.getEndDate(),
                        daysUntil
                );
                leaveRequest.setLastReminderSentForDate(leaveRequest.getStartDate());
                updated.add(leaveRequest);
            } catch (Exception ex) {
                logger.warn("Leave reminder email failed tenant={} leaveId={} reason={}",
                        tenantKey,
                        leaveRequest.getId(),
                        ex.getMessage());
            }
        }

        if (!updated.isEmpty()) {
            leaveRequestRepository.saveAll(updated);
        }
    }

    private void processTaskReminders(String tenantKey, LocalDate today) {
        if (taskReminderDays.isEmpty()) {
            return;
        }

        LocalDate fromDate = today.plusDays(Collections.min(taskReminderDays));
        LocalDate toDate = today.plusDays(Collections.max(taskReminderDays));

        List<Task> candidates = taskRepository.findUpcomingDueTasksForReminder(
                TaskStatus.DONE,
                fromDate,
                toDate
        );

        if (candidates.isEmpty()) {
            return;
        }

        List<Task> updated = new ArrayList<>();
        for (Task task : candidates) {
            if (task.getAssignee() == null || task.getDueDate() == null) {
                continue;
            }

            long daysUntil = ChronoUnit.DAYS.between(today, task.getDueDate());
            if (!taskReminderDays.contains(daysUntil)) {
                continue;
            }

            Employee assignee = task.getAssignee();
            try {
                emailNotificationService.sendTaskDueSoonEmailOrThrow(
                        assignee.getEmail(),
                        buildFullName(assignee),
                        task.getTitle(),
                        task.getDueDate(),
                        daysUntil
                );
                task.setLastDueReminderSentForDate(task.getDueDate());
                updated.add(task);
            } catch (Exception ex) {
                logger.warn("Task due reminder email failed tenant={} taskId={} reason={}",
                        tenantKey,
                        task.getId(),
                        ex.getMessage());
            }
        }

        if (!updated.isEmpty()) {
            taskRepository.saveAll(updated);
        }
    }

    private Set<Long> parseDayConfig(String rawValue) {
        Set<Long> values = new TreeSet<>();
        if (rawValue == null || rawValue.isBlank()) {
            return values;
        }
        for (String token : rawValue.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                long value = Long.parseLong(trimmed);
                if (value >= 0) {
                    values.add(value);
                }
            } catch (NumberFormatException ignored) {
                logger.warn("Ignoring invalid reminder day value '{}'", trimmed);
            }
        }
        return values;
    }

    private String buildFullName(Employee employee) {
        if (employee == null) {
            return "-";
        }
        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? employee.getEmail() : fullName;
    }
}
