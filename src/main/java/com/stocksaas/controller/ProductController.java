package com.stocksaas.controller;

import com.stocksaas.dto.CreateProductRequest;
import com.stocksaas.dto.PageResponse;
import com.stocksaas.dto.ProductDTO;
import com.stocksaas.dto.UpdateProductRequest;
import com.stocksaas.model.User;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.repository.UserWarehouseRepository;
import com.stocksaas.security.SecurityAccessService;
import com.stocksaas.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller pour la gestion des produits
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Produits", description = "API pour la gestion des produits")
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
public class ProductController {
    
    private final ProductService productService;
    private final UserRepository userRepository;
    private final UserWarehouseRepository userWarehouseRepository;
    private final SecurityAccessService securityAccessService;
    
    @PostMapping
    @Operation(summary = "Créer un produit", description = "Crée un nouveau produit pour l'entreprise de l'utilisateur connecté")
    public ResponseEntity<?> createProduct(@Valid @RequestBody CreateProductRequest request) {
        try {
            User user = securityAccessService.requireAdminEntreprise();
            Long companyId = securityAccessService.requireCompanyId(user);
            ProductDTO product = productService.createProduct(companyId, request);
            return ResponseEntity.ok(product);
        } catch (RuntimeException e) {
            log.error("Erreur lors de la création du produit", e);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur inattendue lors de la création du produit", e);
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de la création du produit"));
        }
    }
    
    @GetMapping
    @Operation(summary = "Liste des produits", description = "Liste complète (all=true ou sans page/size) ou paginée (page + size), avec filtres optionnels")
    public ResponseEntity<?> getAllProducts(
            @RequestParam(required = false) Boolean all,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean lowStock) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).build();
            }
            String email = authentication.getName();
            User user = userRepository.findByEmailWithCompanyAndRole(email).orElse(null);
            if (user == null || user.getCompany() == null) {
                return ResponseEntity.ok(List.of());
            }
            Long companyId = user.getCompany().getId();

            boolean hasFilter = (name != null && !name.isBlank())
                    || (reference != null && !reference.isBlank())
                    || (sku != null && !sku.isBlank())
                    || (categoryCode != null && !categoryCode.isBlank())
                    || dateFrom != null
                    || dateTo != null
                    || Boolean.TRUE.equals(lowStock);

            if (page != null && size != null && size > 0) {
                PageResponse<ProductDTO> response = productService.getProductsPaged(
                        companyId, page, size, name, reference, sku, categoryCode, dateFrom, dateTo, lowStock);
                return ResponseEntity.ok(response);
            }

            List<ProductDTO> products;
            if (Boolean.TRUE.equals(all) || hasFilter) {
                log.debug("Filtrage des produits - name: {}, reference: {}, sku: {}, categoryCode: {}, dateFrom: {}, dateTo: {}",
                        name, reference, sku, categoryCode, dateFrom, dateTo);
                products = productService.getAllProductsByCompanyWithFilters(
                        companyId, name, reference, sku, categoryCode, dateFrom, dateTo, lowStock);
            } else {
                products = productService.getAllProductsByCompany(companyId);
            }
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des produits", e);
            return ResponseEntity.ok(List.of());
        }
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un produit", description = "Récupère les détails d'un produit par son ID")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        try {
            User user = securityAccessService.requireAuthenticatedUser();
            Long companyId = securityAccessService.requireCompanyId(user);
            ProductDTO product = productService.getProductById(companyId, id);
            return ResponseEntity.ok(product);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue"));
        }
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un produit", description = "Met à jour les informations d'un produit existant")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        try {
            User user = securityAccessService.requireAdminEntreprise();
            Long companyId = securityAccessService.requireCompanyId(user);
            ProductDTO product = productService.updateProduct(companyId, id, request);
            return ResponseEntity.ok(product);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de la mise à jour"));
        }
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un produit", description = "Supprime logiquement un produit (soft delete)")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        try {
            User user = securityAccessService.requireAdminEntreprise();
            Long companyId = securityAccessService.requireCompanyId(user);
            productService.deleteProduct(companyId, id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}
