package com.stocksaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionRecordDTO {

    private Long id;
    @JsonIgnore
    private String planCode;
    @JsonIgnore
    private String planLabel;
    private String durationCode;
    private String durationLabel;
    private Integer months;
    private Double amountPaid;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String subscribedByEmail;
    private LocalDateTime createdAt;
    private String requestStatus;
    private String requestStatusLabel;
    private String paymentProvider;
    private String paymentProviderLabel;
    private String proofUrl;
    private Long companyId;
    private String companyName;
    private String validatedByEmail;
    private LocalDateTime validatedAt;
    private String rejectionReason;
}
