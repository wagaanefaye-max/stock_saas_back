package com.stocksaas.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class UpdateInvoiceRequest {
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private String status; // DRAFT, SENT, PAID, CANCELLED
    private String notes;
    @Valid
    private List<CreateInvoiceRequest.InvoiceLineRequest> lines;
}
