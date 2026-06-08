package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDTO {
    private Long id;
    private Long clientId;
    private String clientName;
    private String clientEmail;
    private String clientPhone;
    private String clientAddress;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private String status;
    private String statusLabel;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal total;
    private String currency;
    private String notes;
    private List<InvoiceLineDTO> lines;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /**
     * URL publique (avec token) permettant au client de télécharger la facture en PDF.
     * Renseignée uniquement au moment de la création d'une facture déjà payée.
     */
    private String publicDownloadUrl;
}
