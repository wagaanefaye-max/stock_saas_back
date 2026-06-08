package com.stocksaas.service;

import com.stocksaas.dto.*;
import com.stocksaas.model.*;
import com.stocksaas.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final CompanyRepository companyRepository;
    private final PartnerRepository partnerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final MovementService movementService;
    private final EmailService emailService;
    private final InvoiceDownloadTokenStore invoiceDownloadTokenStore;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    private static String statusToLabel(String status) {
        if (status == null) return "";
        return switch (status) {
            case "SENT" -> "Envoyée";
            case "PAID" -> "Payée";
            case "CANCELLED" -> "Annulée";
            default -> "Brouillon";
        };
    }

    @Transactional
    public InvoiceDTO create(Long companyId, Long userId, CreateInvoiceRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));
        if (company.getIsDeleted()) {
            throw new RuntimeException("L'entreprise a été supprimée");
        }
        Partner client;
        if (request.getClientId() != null) {
            client = partnerRepository.findById(request.getClientId())
                    .orElseThrow(() -> new RuntimeException("Client non trouvé"));
            if (client.getIsDeleted() || !companyId.equals(client.getCompany().getId())) {
                throw new RuntimeException("Client non trouvé ou n'appartient pas à cette entreprise");
            }
            if (!"CLIENT".equals(client.getRole())) {
                throw new RuntimeException("Le partenaire sélectionné n'est pas un client");
            }
        } else {
            client = getOrCreateDefaultClient(company);
        }
        boolean isPaid = request.getStatus() != null && "PAID".equals(request.getStatus());
        if (isPaid && request.getDueDate() == null) {
            throw new RuntimeException("La date d'échéance est obligatoire lorsque la facture est marquée comme payée");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new RuntimeException("La facture doit contenir au moins une ligne");
        }

        String invoiceNumber = nextInvoiceNumber(companyId, request.getInvoiceDate().getYear());

        User createdByUser = userRepository.findById(userId)
                .orElse(null);
        Invoice invoice = new Invoice();
        invoice.setCompany(company);
        invoice.setClient(client);
        invoice.setCreatedBy(createdByUser);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setStatus(request.getStatus() != null && "PAID".equals(request.getStatus()) ? "PAID" : "DRAFT");
        invoice.setCurrency("FCFA");
        invoice.setNotes(request.getNotes());
        invoice.setSubtotal(BigDecimal.ZERO);
        invoice.setTaxAmount(BigDecimal.ZERO);
        invoice.setTotal(BigDecimal.ZERO);
        invoice.setIsDeleted(false);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CreateInvoiceRequest.InvoiceLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findByIdWithStockLevels(lineReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Produit non trouvé: " + lineReq.getProductId()));
            if (product.getIsDeleted() || !companyId.equals(product.getCompany().getId())) {
                throw new RuntimeException("Produit non trouvé ou n'appartient pas à cette entreprise");
            }
            BigDecimal availableStock = getTotalStock(product);
            BigDecimal qty = lineReq.getQuantity() != null ? lineReq.getQuantity() : BigDecimal.ONE;
            if (qty.compareTo(availableStock) > 0) {
                throw new RuntimeException("Stock insuffisant pour « " + product.getName() + " » : disponible " + availableStock + ", demandé " + qty);
            }
            BigDecimal unitPrice = lineReq.getUnitPrice() != null && lineReq.getUnitPrice().compareTo(BigDecimal.ZERO) >= 0
                    ? lineReq.getUnitPrice()
                    : (product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO);
            BigDecimal amount = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

            InvoiceLine line = new InvoiceLine();
            line.setInvoice(invoice);
            line.setProduct(product);
            line.setDescription(product.getName());
            line.setQuantity(qty);
            line.setUnitPrice(unitPrice);
            line.setAmount(amount);
            line.setIsDeleted(false);
            invoice.getLines().add(line);
            subtotal = subtotal.add(amount);
        }
        invoice.setSubtotal(subtotal);
        invoice.setTotal(subtotal);
        invoice = invoiceRepository.save(invoice);

        String publicDownloadUrl = null;

        // Créer les sorties de stock uniquement si la facture est marquée comme payée
        if ("PAID".equals(invoice.getStatus())) {
            for (InvoiceLine line : invoice.getLines()) {
                createStockExitForLine(companyId, userId, invoice.getInvoiceNumber(), invoice.getInvoiceDate(), line);
            }
            // Générer un lien de téléchargement public (utilisé pour l'email et WhatsApp)
            String token = invoiceDownloadTokenStore.generateToken(invoice.getId());
            publicDownloadUrl = appBaseUrl.replaceAll("/$", "") + "/api/public/invoices/pdf?token=" + token;
            String clientEmail = invoice.getClient().getEmail();
            String clientName = invoice.getClient().getName();
            emailService.sendInvoicePaidWithDownloadLink(clientEmail, clientName, invoice.getInvoiceNumber(), publicDownloadUrl);
        }

        InvoiceDTO dto = mapToDTO(invoice);
        dto.setPublicDownloadUrl(publicDownloadUrl);
        return dto;
    }

    /**
     * Crée un ou plusieurs mouvements de type Sortie pour une ligne de facture (décrémente le stock).
     */
    private void createStockExitForLine(Long companyId, Long userId, String invoiceNumber, LocalDate invoiceDate, InvoiceLine line) {
        Product product = line.getProduct();
        if (product.getStockLevels() == null || product.getStockLevels().isEmpty()) {
            return;
        }
        BigDecimal remaining = line.getQuantity();
        List<StockLevel> levels = product.getStockLevels().stream()
                .filter(sl -> sl.getQuantity() != null && sl.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(StockLevel::getQuantity).reversed())
                .collect(Collectors.toList());
        for (StockLevel sl : levels) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal deduct = remaining.min(sl.getQuantity());
            if (deduct.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            CreateMovementRequest movReq = CreateMovementRequest.builder()
                    .typeCode("SORTIE")
                    .productId(product.getId())
                    .quantity(deduct)
                    .date(invoiceDate)
                    .warehouseId(sl.getWarehouse().getId())
                    .justification("Facture " + invoiceNumber)
                    .build();
            movementService.createMovement(companyId, userId, movReq);
            remaining = remaining.subtract(deduct);
        }
    }

    private String nextInvoiceNumber(Long companyId, int year) {
        // Inclure les factures supprimées pour ne jamais réutiliser un numéro (contrainte unique en base)
        long count = invoiceRepository.countByYearIncludingDeleted(year);
        return String.format("FAC-%d-%04d", year, count + 1);
    }

    /**
     * Retourne le client par défaut (débit générique) pour une entreprise.
     * Crée un partenaire \"Client comptoir\" (rôle CLIENT) s'il n'existe pas encore.
     */
    private Partner getOrCreateDefaultClient(Company company) {
        Long companyId = company.getId();
        List<Partner> clients = partnerRepository.findByCompanyIdAndRoleAndNotDeleted(companyId, "CLIENT");
        for (Partner p : clients) {
            if (p.getName() != null && p.getName().trim().equalsIgnoreCase("Client comptoir")) {
                return p;
            }
        }
        Partner p = new Partner();
        p.setCompany(company);
        p.setRole("CLIENT");
        p.setName("Client comptoir");
        p.setEmail(null);
        p.setPhone(null);
        p.setAddress(null);
        p.setDescription("Client générique pour les ventes sans client nominatif");
        p.setIsDeleted(false);
        return partnerRepository.save(p);
    }

    @Transactional(readOnly = true)
    public List<InvoiceDTO> findAllByCompany(Long companyId, String status, String invoiceNumber, String clientName) {
        String normalizedStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String normalizedInvoiceNumber = (invoiceNumber != null && !invoiceNumber.isBlank()) ? invoiceNumber.trim() : null;
        String normalizedClientName = (clientName != null && !clientName.isBlank()) ? clientName.trim().toLowerCase() : null;

        List<Invoice> list = invoiceRepository.findByCompanyIdAndNotDeletedWithClientAndLines(companyId);

        // Filtre statut
        if (normalizedStatus != null) {
            list = list.stream()
                    .filter(i -> {
                        String s = i.getStatus();
                        if (s == null) return false;
                        s = s.toUpperCase();
                        if ("UNPAID".equals(normalizedStatus)) {
                            // Impayées = toutes sauf PAID
                            return !"PAID".equals(s);
                        }
                        return s.equals(normalizedStatus);
                    })
                    .toList();
        }

        // Filtre numéro de facture
        if (normalizedInvoiceNumber != null) {
            String q = normalizedInvoiceNumber.toLowerCase();
            list = list.stream()
                    .filter(i -> i.getInvoiceNumber() != null
                            && i.getInvoiceNumber().toLowerCase().contains(q))
                    .toList();
        }

        // Filtre nom/prénom client
        if (normalizedClientName != null) {
            String q = normalizedClientName;
            list = list.stream()
                    .filter(i -> i.getClient() != null
                            && i.getClient().getName() != null
                            && i.getClient().getName().toLowerCase().contains(q))
                    .toList();
        }

        return list.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InvoiceDTO getById(Long companyId, Long id) {
        Invoice invoice = invoiceRepository.findByIdWithLinesAndProduct(id)
                .orElseThrow(() -> new RuntimeException("Facture non trouvée"));
        if (invoice.getIsDeleted() || !companyId.equals(invoice.getCompany().getId())) {
            throw new RuntimeException("Facture non trouvée");
        }
        return mapToDTO(invoice);
    }

    @Transactional
    public InvoiceDTO update(Long companyId, Long userId, Long id, UpdateInvoiceRequest request) {
        Invoice invoice = invoiceRepository.findByIdWithLinesAndProduct(id)
                .orElseThrow(() -> new RuntimeException("Facture non trouvée"));
        if (invoice.getIsDeleted() || !companyId.equals(invoice.getCompany().getId())) {
            throw new RuntimeException("Facture non trouvée");
        }
        if (!"DRAFT".equals(invoice.getStatus())) {
            throw new RuntimeException("Seules les factures au statut Brouillon peuvent être modifiées");
        }
        String originalStatus = invoice.getStatus();
        if (request.getInvoiceDate() != null) invoice.setInvoiceDate(request.getInvoiceDate());
        if (request.getDueDate() != null) invoice.setDueDate(request.getDueDate());
        if (request.getStatus() != null && List.of("DRAFT", "SENT", "PAID", "CANCELLED").contains(request.getStatus())) {
            invoice.setStatus(request.getStatus());
        }
        if (request.getNotes() != null) invoice.setNotes(request.getNotes());

        if (request.getLines() != null && !request.getLines().isEmpty()) {
            invoice.getLines().clear();
            BigDecimal subtotal = BigDecimal.ZERO;
            for (CreateInvoiceRequest.InvoiceLineRequest lineReq : request.getLines()) {
                Product product = productRepository.findByIdWithStockLevels(lineReq.getProductId())
                        .orElseThrow(() -> new RuntimeException("Produit non trouvé"));
                if (product.getIsDeleted() || !companyId.equals(product.getCompany().getId())) {
                    throw new RuntimeException("Produit non trouvé ou n'appartient pas à cette entreprise");
                }
                BigDecimal availableStock = getTotalStock(product);
                BigDecimal qty = lineReq.getQuantity() != null ? lineReq.getQuantity() : BigDecimal.ONE;
                if (qty.compareTo(availableStock) > 0) {
                    throw new RuntimeException("Stock insuffisant pour « " + product.getName() + " » : disponible " + availableStock + ", demandé " + qty);
                }
                BigDecimal unitPrice = lineReq.getUnitPrice() != null && lineReq.getUnitPrice().compareTo(BigDecimal.ZERO) >= 0
                        ? lineReq.getUnitPrice()
                        : (product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO);
                BigDecimal amount = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

                InvoiceLine line = new InvoiceLine();
                line.setInvoice(invoice);
                line.setProduct(product);
                line.setDescription(product.getName());
                line.setQuantity(qty);
                line.setUnitPrice(unitPrice);
                line.setAmount(amount);
                line.setIsDeleted(false);
                invoice.getLines().add(line);
                subtotal = subtotal.add(amount);
            }
            invoice.setSubtotal(subtotal);
            invoice.setTotal(subtotal);
        }
        invoice = invoiceRepository.save(invoice);

        boolean becamePaid = !"PAID".equals(originalStatus) && "PAID".equals(invoice.getStatus());
        String publicDownloadUrl = null;
        if (becamePaid) {
            if (invoice.getDueDate() == null) {
                throw new RuntimeException("La date d'échéance est obligatoire lorsque la facture est marquée comme payée");
            }
            for (InvoiceLine line : invoice.getLines()) {
                createStockExitForLine(companyId, userId, invoice.getInvoiceNumber(), invoice.getInvoiceDate(), line);
            }
            String token = invoiceDownloadTokenStore.generateToken(invoice.getId());
            publicDownloadUrl = appBaseUrl.replaceAll("/$", "") + "/api/public/invoices/pdf?token=" + token;
            String clientEmail = invoice.getClient().getEmail();
            String clientName = invoice.getClient().getName();
            emailService.sendInvoicePaidWithDownloadLink(clientEmail, clientName, invoice.getInvoiceNumber(), publicDownloadUrl);
        }

        InvoiceDTO dto = mapToDTO(invoice);
        dto.setPublicDownloadUrl(publicDownloadUrl);
        return dto;
    }

    @Transactional
    public void delete(Long companyId, Long id) {
        Invoice invoice = invoiceRepository.findByIdWithLinesAndProduct(id)
                .orElseThrow(() -> new RuntimeException("Facture non trouvée"));
        if (invoice.getIsDeleted() || !companyId.equals(invoice.getCompany().getId())) {
            throw new RuntimeException("Facture non trouvée");
        }
        invoice.setIsDeleted(true);
        invoiceRepository.save(invoice);
    }

    private BigDecimal getTotalStock(Product product) {
        if (product.getStockLevels() == null || product.getStockLevels().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return product.getStockLevels().stream()
                .map(StockLevel::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private InvoiceDTO mapToDTO(Invoice i) {
        return InvoiceDTO.builder()
                .id(i.getId())
                .clientId(i.getClient().getId())
                .clientName(i.getClient().getName())
                .clientEmail(i.getClient().getEmail())
                .clientPhone(i.getClient().getPhone())
                .clientAddress(i.getClient().getAddress())
                .invoiceNumber(i.getInvoiceNumber())
                .invoiceDate(i.getInvoiceDate())
                .dueDate(i.getDueDate())
                .status(i.getStatus())
                .statusLabel(statusToLabel(i.getStatus()))
                .subtotal(i.getSubtotal())
                .taxAmount(i.getTaxAmount())
                .total(i.getTotal())
                .currency(i.getCurrency())
                .notes(i.getNotes())
                .lines(i.getLines() != null ? i.getLines().stream().map(this::mapLineToDTO).collect(Collectors.toList()) : List.of())
                .createdById(i.getCreatedBy() != null ? i.getCreatedBy().getId() : null)
                .createdByName(i.getCreatedBy() != null ? i.getCreatedBy().getName() : null)
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }

    private InvoiceLineDTO mapLineToDTO(InvoiceLine l) {
        return InvoiceLineDTO.builder()
                .id(l.getId())
                .productId(l.getProduct() != null ? l.getProduct().getId() : null)
                .productName(l.getProduct() != null ? l.getProduct().getName() : null)
                .productSku(l.getProduct() != null ? l.getProduct().getSku() : null)
                .description(l.getDescription())
                .quantity(l.getQuantity())
                .unitPrice(l.getUnitPrice())
                .amount(l.getAmount())
                .build();
    }
}
