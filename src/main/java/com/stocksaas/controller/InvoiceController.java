package com.stocksaas.controller;

import com.stocksaas.dto.CreateInvoiceRequest;
import com.stocksaas.dto.InvoiceDTO;
import com.stocksaas.dto.UpdateInvoiceRequest;
import com.stocksaas.model.User;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.lowagie.text.DocumentException;
import com.stocksaas.service.InvoicePdfService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Tag(name = "Factures", description = "Création et gestion des factures clients")
@CrossOrigin(
        origins = {
                "http://localhost:4200",
                "http://localhost:3000",
                "http://sen-stocksaas.com",
                "https://sen-stocksaas.com",
                "http://164.132.43.247",
                "http://164.132.43.247:4200"
        },
        allowCredentials = "true"
)
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("Non authentifié");
        }
        User user = userRepository.findByEmailWithCompanyAndRole(auth.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (user.getCompany() == null) {
            throw new RuntimeException("Aucune entreprise associée");
        }
        return user;
    }

    private Long getCompanyId() {
        return getCurrentUser().getCompany().getId();
    }

    @PostMapping
    @Operation(summary = "Créer une facture")
    public ResponseEntity<?> create(@Valid @RequestBody CreateInvoiceRequest request) {
        try {
            User user = getCurrentUser();
            InvoiceDTO dto = invoiceService.create(user.getCompany().getId(), user.getId(), request);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Liste des factures de l'entreprise (filtrable)")
    public ResponseEntity<?> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String clientName
    ) {
        try {
            Long companyId = getCompanyId();
            List<InvoiceDTO> list = invoiceService.findAllByCompany(companyId, status, invoiceNumber, clientName);
            return ResponseEntity.ok(list);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une facture")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            Long companyId = getCompanyId();
            return ResponseEntity.ok(invoiceService.getById(companyId, id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Télécharger la facture en PDF")
    public ResponseEntity<?> getPdf(@PathVariable Long id) {
        try {
            Long companyId = getCompanyId();
            var invoice = invoiceService.getById(companyId, id);
            byte[] pdf = invoicePdfService.generatePdf(invoice);
            String filename = "facture-" + (invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber().replace(" ", "-") : id) + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(pdf.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (DocumentException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Erreur lors de la génération du PDF"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une facture")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody UpdateInvoiceRequest request) {
        try {
            User user = getCurrentUser();
            return ResponseEntity.ok(invoiceService.update(user.getCompany().getId(), user.getId(), id, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer (soft) une facture")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            Long companyId = getCompanyId();
            invoiceService.delete(companyId, id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
