package com.stocksaas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * File d'attente persistante des e-mails à envoyer ou à relancer.
 */
@Entity
@Table(name = "td_pending_emails", indexes = {
        @Index(name = "idx_pending_emails_status_retry", columnList = "status, next_retry_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class PendingEmail extends BaseEntity {

    @Column(name = "to_email", nullable = false, length = 255)
    private String toEmail;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "email_type", nullable = false, length = 50)
    private String emailType;

    @Column(name = "attachment_filename", length = 255)
    private String attachmentFilename;

    @Column(name = "attachment_content_type", length = 100)
    private String attachmentContentType;

    @Lob
    @Column(name = "attachment_data")
    private byte[] attachmentData;

    /** PENDING, SENT, FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}
