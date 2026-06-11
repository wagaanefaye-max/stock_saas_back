package com.stocksaas.service;

import com.stocksaas.mail.PendingEmailStatus;
import com.stocksaas.model.PendingEmail;
import com.stocksaas.repository.PendingEmailRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * File d'attente e-mail : tentative immédiate puis relances périodiques en cas d'échec.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOutboxService {

    private final JavaMailSender mailSender;
    private final PendingEmailRepository pendingEmailRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.mail.retry.interval-minutes:10}")
    private int retryIntervalMinutes;

    @Value("${app.mail.retry.max-attempts:144}")
    private int maxAttempts;

    @Value("${app.mail.retry.batch-size:50}")
    private int batchSize;

    public void enqueueTextEmail(String toEmail, String subject, String body, String emailType) {
        enqueue(toEmail, subject, body, emailType, null, null, null);
    }

    public void enqueueWithAttachment(
            String toEmail,
            String subject,
            String body,
            String emailType,
            String attachmentFilename,
            String attachmentContentType,
            byte[] attachmentData
    ) {
        enqueue(toEmail, subject, body, emailType, attachmentFilename, attachmentContentType, attachmentData);
    }

    @Transactional
    public void enqueue(
            String toEmail,
            String subject,
            String body,
            String emailType,
            String attachmentFilename,
            String attachmentContentType,
            byte[] attachmentData
    ) {
        PendingEmail pending = new PendingEmail();
        pending.setToEmail(toEmail);
        pending.setSubject(subject);
        pending.setBody(body);
        pending.setEmailType(emailType);
        pending.setAttachmentFilename(attachmentFilename);
        pending.setAttachmentContentType(attachmentContentType);
        pending.setAttachmentData(attachmentData);
        pending.setStatus(PendingEmailStatus.PENDING);
        pending.setAttemptCount(0);
        pending.setNextRetryAt(LocalDateTime.now());
        pending.setIsDeleted(false);
        pending = pendingEmailRepository.save(pending);
        deliver(pending);
    }

    @Transactional
    public int processDueRetries() {
        List<PendingEmail> due = pendingEmailRepository.findDueForRetry(
                PendingEmailStatus.PENDING,
                LocalDateTime.now(),
                PageRequest.of(0, batchSize)
        );
        for (PendingEmail email : due) {
            deliver(email);
        }
        return due.size();
    }

    private void deliver(PendingEmail email) {
        try {
            if (hasAttachment(email)) {
                sendMime(email);
            } else {
                sendText(email);
            }
            email.setStatus(PendingEmailStatus.SENT);
            email.setSentAt(LocalDateTime.now());
            email.setLastError(null);
            log.info("Email {} envoyé à {}", email.getEmailType(), email.getToEmail());
        } catch (Exception e) {
            registerFailure(email, e);
        }
        pendingEmailRepository.save(email);
    }

    private void sendText(PendingEmail email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email.getToEmail());
        message.setSubject(email.getSubject());
        message.setText(email.getBody());
        mailSender.send(message);
    }

    private void sendMime(PendingEmail email) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(email.getToEmail());
        helper.setSubject(email.getSubject());
        helper.setText(email.getBody(), false);
        String contentType = email.getAttachmentContentType() != null && !email.getAttachmentContentType().isBlank()
                ? email.getAttachmentContentType()
                : "application/octet-stream";
        helper.addAttachment(
                email.getAttachmentFilename(),
                new ByteArrayResource(email.getAttachmentData()),
                contentType
        );
        mailSender.send(mimeMessage);
    }

    private void registerFailure(PendingEmail email, Exception e) {
        int attempts = email.getAttemptCount() + 1;
        email.setAttemptCount(attempts);
        email.setLastError(truncateError(e.getMessage()));

        if (attempts >= maxAttempts) {
            email.setStatus(PendingEmailStatus.FAILED);
            log.error(
                    "Email {} abandonné après {} tentatives pour {} : {}",
                    email.getEmailType(),
                    attempts,
                    email.getToEmail(),
                    email.getLastError()
            );
        } else {
            email.setStatus(PendingEmailStatus.PENDING);
            email.setNextRetryAt(LocalDateTime.now().plusMinutes(retryIntervalMinutes));
            log.warn(
                    "Échec envoi email {} à {} (tentative {}/{}), prochain essai vers {}",
                    email.getEmailType(),
                    email.getToEmail(),
                    attempts,
                    maxAttempts,
                    email.getNextRetryAt()
            );
        }
    }

    private static boolean hasAttachment(PendingEmail email) {
        return email.getAttachmentData() != null
                && email.getAttachmentData().length > 0
                && email.getAttachmentFilename() != null
                && !email.getAttachmentFilename().isBlank();
    }

    private static String truncateError(String message) {
        if (message == null) {
            return "Erreur inconnue";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
