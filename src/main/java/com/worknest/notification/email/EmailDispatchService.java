package com.worknest.notification.email;

import com.worknest.common.exception.EmailServiceUnavailableException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

@Service
public class EmailDispatchService {

    private static final Logger logger = LoggerFactory.getLogger(EmailDispatchService.class);

    private final JavaMailSender javaMailSender;
    private final String defaultFromAddress;
    private final Executor emailExecutor;

    public EmailDispatchService(
            JavaMailSender javaMailSender,
            @Value("${app.email.from:${spring.mail.username:}}") String defaultFromAddress,
            @Qualifier("emailExecutor") Executor emailExecutor) {
        this.javaMailSender = javaMailSender;
        this.defaultFromAddress = defaultFromAddress;
        this.emailExecutor = emailExecutor;
    }

    public void sendHtmlEmailOrThrow(String toEmail, EmailContent emailContent) {
        try {
            sendHtmlEmailInternal(toEmail, emailContent);
        } catch (MessagingException | MailException ex) {
            throw new EmailServiceUnavailableException(
                    "Email service is currently unavailable. Please try again later.", ex);
        }
    }

    public void sendHtmlEmailAsync(String toEmail, EmailContent emailContent) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doAsyncSend(toEmail, emailContent);
                }
            });
            return;
        }
        doAsyncSend(toEmail, emailContent);
    }

    protected void doAsyncSend(String toEmail, EmailContent emailContent) {
        try {
            emailExecutor.execute(() -> {
                try {
                    sendHtmlEmailInternal(toEmail, emailContent);
                } catch (MessagingException | MailException ex) {
                    logger.warn("Async email send failed for {} subject={} reason={}",
                            toEmail,
                            emailContent.subject(),
                            ex.getMessage());
                }
            });
        } catch (RuntimeException ex) {
            logger.warn("Async email dispatch rejected for {} subject={} reason={}",
                    toEmail,
                    emailContent.subject(),
                    ex.getMessage());
        }
    }

    private void sendHtmlEmailInternal(String toEmail, EmailContent emailContent)
            throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                message,
                MimeMessageHelper.MULTIPART_MODE_NO,
                StandardCharsets.UTF_8.name()
        );
        if (defaultFromAddress != null && !defaultFromAddress.isBlank()) {
            helper.setFrom(defaultFromAddress);
        }
        helper.setTo(toEmail);
        helper.setSubject(emailContent.subject());
        helper.setText(emailContent.htmlBody(), true);
        javaMailSender.send(message);
    }
}
