package com.worknest.notification.email;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class EmailNotificationService {

    private final EmailTemplateFactory templateFactory;
    private final EmailDispatchService emailDispatchService;

    public EmailNotificationService(
            EmailTemplateFactory templateFactory,
            EmailDispatchService emailDispatchService) {
        this.templateFactory = templateFactory;
        this.emailDispatchService = emailDispatchService;
    }

    public void sendPasswordResetLinkEmailOrThrow(
            String toEmail,
            String fullName,
            String resetLink,
            int expiryMinutes) {
        EmailContent content = templateFactory.passwordResetLink(fullName, resetLink, expiryMinutes);
        emailDispatchService.sendHtmlEmailOrThrow(toEmail, content);
    }

    public void sendTemporaryPasswordEmailOrThrow(
            String toEmail,
            String fullName,
            String temporaryPassword) {
        EmailContent content = templateFactory.temporaryPassword(fullName, temporaryPassword);
        emailDispatchService.sendHtmlEmailOrThrow(toEmail, content);
    }

    public void sendLeaveApprovedEmail(
            String toEmail,
            String fullName,
            LocalDate startDate,
            LocalDate endDate,
            String approverName) {
        EmailContent content = templateFactory.leaveApproved(fullName, startDate, endDate, approverName);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendLeaveRejectedEmail(
            String toEmail,
            String fullName,
            LocalDate startDate,
            LocalDate endDate,
            String approverName,
            String rejectionReason) {
        EmailContent content =
                templateFactory.leaveRejected(fullName, startDate, endDate, approverName, rejectionReason);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendLeaveCancelledAlertEmail(
            String toEmail,
            String recipientName,
            String employeeName,
            LocalDate startDate,
            LocalDate endDate) {
        EmailContent content =
                templateFactory.leaveCancelledAlert(recipientName, employeeName, startDate, endDate);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendLeaveReminderEmail(
            String toEmail,
            String recipientName,
            LocalDate startDate,
            LocalDate endDate,
            long daysUntilStart) {
        EmailContent content =
                templateFactory.leaveReminder(recipientName, startDate, endDate, daysUntilStart);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendLeaveReminderEmailOrThrow(
            String toEmail,
            String recipientName,
            LocalDate startDate,
            LocalDate endDate,
            long daysUntilStart) {
        EmailContent content =
                templateFactory.leaveReminder(recipientName, startDate, endDate, daysUntilStart);
        emailDispatchService.sendHtmlEmailOrThrow(toEmail, content);
    }

    public void sendTaskAssignedEmail(
            String toEmail,
            String recipientName,
            String title,
            String description,
            LocalDate dueDate,
            String priority,
            String assigneeName,
            String assignerName) {
        EmailContent content = templateFactory.taskAssigned(
                recipientName,
                title,
                description,
                dueDate,
                priority,
                assigneeName,
                assignerName
        );
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendTaskDueSoonEmail(
            String toEmail,
            String recipientName,
            String title,
            LocalDate dueDate,
            long daysUntilDue) {
        EmailContent content = templateFactory.taskDueSoon(recipientName, title, dueDate, daysUntilDue);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendTaskDueSoonEmailOrThrow(
            String toEmail,
            String recipientName,
            String title,
            LocalDate dueDate,
            long daysUntilDue) {
        EmailContent content = templateFactory.taskDueSoon(recipientName, title, dueDate, daysUntilDue);
        emailDispatchService.sendHtmlEmailOrThrow(toEmail, content);
    }

    public void sendTaskStatusChangedEmail(
            String toEmail,
            String recipientName,
            String title,
            String newStatus,
            String actorName) {
        EmailContent content =
                templateFactory.taskStatusChanged(recipientName, title, newStatus, actorName);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendTaskCommentAddedEmail(
            String toEmail,
            String recipientName,
            String taskTitle,
            String commenterName,
            String comment) {
        EmailContent content =
                templateFactory.taskCommentAdded(recipientName, taskTitle, commenterName, comment);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendTeamAddedEmail(
            String toEmail,
            String recipientName,
            String teamName,
            String teamLeader,
            int activeMemberCount) {
        EmailContent content =
                templateFactory.teamAdded(recipientName, teamName, teamLeader, activeMemberCount);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendTeamRemovedEmail(
            String toEmail,
            String recipientName,
            String teamName) {
        EmailContent content = templateFactory.teamRemoved(recipientName, teamName);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendTeamLeaderChangedEmail(
            String toEmail,
            String recipientName,
            String teamName,
            String newLeaderName) {
        EmailContent content =
                templateFactory.teamLeaderChanged(recipientName, teamName, newLeaderName);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendCompanyAnnouncementEmail(
            String toEmail,
            String recipientName,
            String title,
            String message,
            String createdBy) {
        EmailContent content =
                templateFactory.companyAnnouncement(recipientName, title, message, createdBy);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendTeamAnnouncementEmail(
            String toEmail,
            String recipientName,
            String teamName,
            String title,
            String message,
            String createdBy) {
        EmailContent content =
                templateFactory.teamAnnouncement(recipientName, teamName, title, message, createdBy);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendHrMentionEmail(
            String toEmail,
            String recipientName,
            String senderName,
            String messageSnippet) {
        EmailContent content = templateFactory.hrMention(recipientName, senderName, messageSnippet);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }

    public void sendAttendanceConfirmationEmail(
            String toEmail,
            String recipientName,
            String action,
            LocalDateTime actionTime) {
        EmailContent content =
                templateFactory.attendanceConfirmation(recipientName, action, actionTime);
        emailDispatchService.sendHtmlEmailAsync(toEmail, content);
    }
}
