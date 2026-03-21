package com.worknest.notification.email;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EmailTemplateFactory {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public EmailContent passwordResetLink(
            String fullName,
            String resetLink,
            int expiryMinutes) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Reset link", "<a href=\"" + escape(resetLink) + "\">Reset password</a>");
        details.put("Expires in", expiryMinutes + " minutes");
        return build(
                "WorkNest password reset request",
                "Use the secure link below to reset your password.",
                "Password reset requested",
                fullName,
                details,
                "If you did not request this, you can ignore this email."
        );
    }

    public EmailContent temporaryPassword(String fullName, String temporaryPassword) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Temporary password", "<strong>" + escape(temporaryPassword) + "</strong>");
        details.put("Required action", "You must change this password on next login.");
        return build(
                "WorkNest temporary password issued",
                "An administrator has issued a temporary password for your account.",
                "Temporary password generated",
                fullName,
                details,
                "For security, change your password immediately after sign in."
        );
    }

    public EmailContent leaveApproved(
            String fullName,
            LocalDate startDate,
            LocalDate endDate,
            String approverName) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Start date", formatDate(startDate));
        details.put("End date", formatDate(endDate));
        details.put("Approved by", escapeOrDash(approverName));
        return build(
                "Leave request approved",
                "Your leave request has been approved.",
                "Leave approved",
                fullName,
                details,
                "Please coordinate handover tasks with your team if required."
        );
    }

    public EmailContent leaveRejected(
            String fullName,
            LocalDate startDate,
            LocalDate endDate,
            String approverName,
            String rejectionReason) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Start date", formatDate(startDate));
        details.put("End date", formatDate(endDate));
        details.put("Reviewed by", escapeOrDash(approverName));
        details.put("Reason", escapeOrDash(rejectionReason));
        return build(
                "Leave request rejected",
                "Your leave request was rejected.",
                "Leave rejected",
                fullName,
                details,
                "You can submit a new request with revised dates or details."
        );
    }

    public EmailContent leaveCancelledAlert(
            String recipientName,
            String employeeName,
            LocalDate startDate,
            LocalDate endDate) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Employee", escapeOrDash(employeeName));
        details.put("Start date", formatDate(startDate));
        details.put("End date", formatDate(endDate));
        return build(
                "Leave request cancelled",
                "A leave request has been cancelled.",
                "Leave cancellation alert",
                recipientName,
                details,
                "No further action is required unless roster planning is affected."
        );
    }

    public EmailContent leaveReminder(
            String fullName,
            LocalDate startDate,
            LocalDate endDate,
            long daysUntilStart) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Start date", formatDate(startDate));
        details.put("End date", formatDate(endDate));
        details.put("Starts in", daysUntilStart + " day(s)");
        return build(
                "Upcoming leave reminder",
                "Your approved leave is coming up soon.",
                "Upcoming leave",
                fullName,
                details,
                "Ensure your pending work items are handed over before leave starts."
        );
    }

    public EmailContent taskAssigned(
            String recipientName,
            String title,
            String description,
            LocalDate dueDate,
            String priority,
            String assigneeName,
            String assignerName) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Task", escapeOrDash(title));
        details.put("Description", escapeOrDash(description));
        details.put("Deadline", formatDate(dueDate));
        details.put("Priority", escapeOrDash(priority));
        details.put("Assignee", escapeOrDash(assigneeName));
        details.put("Assigned by", escapeOrDash(assignerName));
        return build(
                "Task assigned: " + safeSubjectSuffix(title),
                "A task has been assigned in WorkNest.",
                "Task assignment",
                recipientName,
                details,
                "Review the task and update status as work progresses."
        );
    }

    public EmailContent taskDueSoon(
            String recipientName,
            String title,
            LocalDate dueDate,
            long daysUntilDue) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Task", escapeOrDash(title));
        details.put("Due date", formatDate(dueDate));
        details.put("Due in", daysUntilDue + " day(s)");
        return build(
                "Task due soon: " + safeSubjectSuffix(title),
                "A task deadline is approaching.",
                "Task reminder",
                recipientName,
                details,
                "Please update progress and complete before the due date."
        );
    }

    public EmailContent taskStatusChanged(
            String recipientName,
            String title,
            String newStatus,
            String actorName) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Task", escapeOrDash(title));
        details.put("New status", escapeOrDash(newStatus));
        details.put("Updated by", escapeOrDash(actorName));
        return build(
                "Task status updated: " + safeSubjectSuffix(title),
                "A task status was changed.",
                "Task status changed",
                recipientName,
                details,
                "Open WorkNest to review full activity."
        );
    }

    public EmailContent taskCommentAdded(
            String recipientName,
            String title,
            String commenterName,
            String comment) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Task", escapeOrDash(title));
        details.put("Commented by", escapeOrDash(commenterName));
        details.put("Comment", escapeOrDash(comment));
        return build(
                "New task comment: " + safeSubjectSuffix(title),
                "A new comment was added to a task.",
                "Task comment added",
                recipientName,
                details,
                "Review the comment and update task progress if needed."
        );
    }

    public EmailContent teamAdded(
            String recipientName,
            String teamName,
            String teamLeader,
            int activeMemberCount) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Team", escapeOrDash(teamName));
        details.put("Team leader", escapeOrDash(teamLeader));
        details.put("Current members", String.valueOf(activeMemberCount));
        return build(
                "You were added to team: " + safeSubjectSuffix(teamName),
                "Your team assignment has been updated.",
                "Added to team",
                recipientName,
                details,
                "Check team tasks and communication channels in WorkNest."
        );
    }

    public EmailContent teamRemoved(
            String recipientName,
            String teamName) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Team", escapeOrDash(teamName));
        return build(
                "You were removed from team: " + safeSubjectSuffix(teamName),
                "Your team assignment has changed.",
                "Removed from team",
                recipientName,
                details,
                "If this was unexpected, please contact your HR or admin team."
        );
    }

    public EmailContent teamLeaderChanged(
            String recipientName,
            String teamName,
            String newLeaderName) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Team", escapeOrDash(teamName));
        details.put("New team leader", escapeOrDash(newLeaderName));
        return build(
                "Team leader changed: " + safeSubjectSuffix(teamName),
                "Your team now has a new leader.",
                "Team leadership updated",
                recipientName,
                details,
                "Use this update for future escalations and approvals."
        );
    }

    public EmailContent companyAnnouncement(
            String recipientName,
            String title,
            String message,
            String createdBy) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Announcement", escapeOrDash(title));
        details.put("Posted by", escapeOrDash(createdBy));
        details.put("Message", escapeOrDash(message));
        return build(
                "New company announcement: " + safeSubjectSuffix(title),
                "A new company-wide announcement was posted.",
                "Company announcement",
                recipientName,
                details,
                "Open WorkNest for full context and follow-up actions."
        );
    }

    public EmailContent teamAnnouncement(
            String recipientName,
            String teamName,
            String title,
            String message,
            String createdBy) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Team", escapeOrDash(teamName));
        details.put("Announcement", escapeOrDash(title));
        details.put("Posted by", escapeOrDash(createdBy));
        details.put("Message", escapeOrDash(message));
        return build(
                "New team announcement: " + safeSubjectSuffix(title),
                "A new team announcement was posted.",
                "Team announcement",
                recipientName,
                details,
                "Review this update in WorkNest and align with your team."
        );
    }

    public EmailContent hrMention(
            String recipientName,
            String senderName,
            String messageSnippet) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Mentioned by", escapeOrDash(senderName));
        details.put("Message", escapeOrDash(messageSnippet));
        return build(
                "You were mentioned in HR chat",
                "Someone mentioned you in an HR chat thread.",
                "HR chat mention",
                recipientName,
                details,
                "Open WorkNest HR chat to respond."
        );
    }

    public EmailContent attendanceConfirmation(
            String recipientName,
            String action,
            LocalDateTime actionTime) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Action", escapeOrDash(action));
        details.put("Timestamp", formatDateTime(actionTime));
        return build(
                "Attendance " + safeSubjectSuffix(action),
                "Your attendance activity has been recorded.",
                "Attendance confirmation",
                recipientName,
                details,
                "Keep this record for your attendance history."
        );
    }

    private EmailContent build(
            String subject,
            String preheader,
            String heading,
            String fullName,
            Map<String, String> details,
            String footerNote) {
        StringBuilder detailsRows = new StringBuilder();
        for (Map.Entry<String, String> entry : details.entrySet()) {
            detailsRows.append("<tr><td style=\"padding:8px 0;color:#4a5568;font-size:14px;\">")
                    .append(escape(entry.getKey()))
                    .append("</td><td style=\"padding:8px 0;color:#1a202c;font-size:14px;font-weight:600;\">")
                    .append(entry.getValue() == null ? "-" : entry.getValue())
                    .append("</td></tr>");
        }

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f5f7fb;font-family:Segoe UI,Arial,sans-serif;color:#1a202c;">
                  <div style="max-width:640px;margin:24px auto;padding:0 16px;">
                    <div style="background:#ffffff;border-radius:12px;border:1px solid #e2e8f0;overflow:hidden;">
                      <div style="padding:24px 24px 12px 24px;background:#0f172a;color:#ffffff;">
                        <p style="margin:0;font-size:12px;opacity:0.8;">%s</p>
                        <h1 style="margin:8px 0 0 0;font-size:22px;line-height:1.2;">%s</h1>
                      </div>
                      <div style="padding:24px;">
                        <p style="margin:0 0 16px 0;font-size:14px;">Hello %s,</p>
                        <table style="width:100%%;border-collapse:collapse;">%s</table>
                        <p style="margin:16px 0 0 0;font-size:13px;color:#4a5568;">%s</p>
                      </div>
                    </div>
                    <p style="text-align:center;color:#718096;font-size:12px;margin:12px 0 0 0;">WorkNest Notification</p>
                  </div>
                </body>
                </html>
                """.formatted(
                escape(subject),
                escape(preheader),
                escape(heading),
                escapeOrDash(fullName),
                detailsRows,
                escapeOrDash(footerNote)
        );

        return new EmailContent(subject, html);
    }

    private String safeSubjectSuffix(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? "Update" : normalized;
    }

    private String escapeOrDash(String value) {
        if (value == null || value.trim().isBlank()) {
            return "-";
        }
        return escape(value.trim());
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : DATE_FORMAT.format(date);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "-" : DATE_TIME_FORMAT.format(dateTime);
    }

    private String escape(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
