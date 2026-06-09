package com.stocksaas.controller;

import com.lowagie.text.DocumentException;
import com.stocksaas.dto.InvoiceDTO;
import com.stocksaas.model.Invoice;
import com.stocksaas.repository.InvoiceRepository;
import com.stocksaas.service.InvoiceDownloadTokenStore;
import com.stocksaas.service.InvoicePdfService;
import com.stocksaas.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints publics pour le téléchargement de facture via lien envoyé par email (sans authentification).
 */
@RestController
@RequestMapping("/api/public/invoices")
@RequiredArgsConstructor
@Tag(name = "Factures (public)", description = "Téléchargement de facture par lien email")
@CrossOrigin(
        origins = {
                "http://localhost:4200",
                "http://localhost:3000",
                "http://sen-stocksaas.com",
                "https://sen-stocksaas.com",
                "http://164.132.43.247",
                "http://164.132.43.247:4200"
        }
)
public class PublicInvoiceController {

    private final InvoiceDownloadTokenStore invoiceDownloadTokenStore;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Télécharger une facture en PDF avec token (lien envoyé par email)")
    public ResponseEntity<?> getPdfByToken(@RequestParam String token) {
        Long invoiceId = invoiceDownloadTokenStore.getInvoiceIdIfValid(token);
        if (invoiceId == null) {
            return ResponseEntity.badRequest().body("Lien invalide ou expiré.");
        }
        Invoice invoice = invoiceRepository.findByIdWithLinesAndProduct(invoiceId).orElse(null);
        if (invoice == null || invoice.getIsDeleted()) {
            return ResponseEntity.notFound().build();
        }
        Long companyId = invoice.getCompany().getId();
        try {
            InvoiceDTO dto = invoiceService.getById(companyId, invoiceId);
            byte[] pdf = invoicePdfService.generatePdf(dto);
            String filename = "facture-" + (dto.getInvoiceNumber() != null ? dto.getInvoiceNumber().replace(" ", "-") : invoiceId) + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(pdf.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (DocumentException e) {
            return ResponseEntity.internalServerError().body("Erreur lors de la génération du PDF.");
        }
    }
}
