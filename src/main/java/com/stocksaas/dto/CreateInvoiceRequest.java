package com.stocksaas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class CreateInvoiceRequest {

    private Long clientId;

    @NotNull(message = "La date de facture est obligatoire")
    private LocalDate invoiceDate;

    private LocalDate dueDate;

    /** Statut initial : DRAFT (défaut) ou PAID pour marquer comme payée à la création */
    @Pattern(regexp = "DRAFT|PAID", message = "Le statut doit être DRAFT ou PAID")
    private String status;

    private String notes;

    @Valid
    private List<InvoiceLineRequest> lines = new ArrayList<>();

    @Data
    public static class InvoiceLineRequest {
        @NotNull(message = "Le produit est obligatoire")
        private Long productId;
        @NotNull(message = "La quantité est obligatoire")
        private java.math.BigDecimal quantity;
        private java.math.BigDecimal unitPrice; // optionnel : pris du produit si null
    }
}
