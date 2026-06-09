package com.stocksaas.controller;

import com.stocksaas.dto.ProductCategoryDTO;
import com.stocksaas.dto.CreateCategoryRequest;
import com.stocksaas.model.ProductCategory;
import com.stocksaas.repository.ProductCategoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Controller pour les catégories de produits (table de référence tp_category)
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Catégories", description = "API pour la liste des catégories de produits")
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
public class CategoryController {

    private static final String GENERAL_CODE = "GENERAL";
    private final ProductCategoryRepository productCategoryRepository;

    @GetMapping
    @Operation(summary = "Liste des catégories", description = "Récupère la liste des catégories de produits actives")
    public ResponseEntity<List<ProductCategoryDTO>> getAllCategories() {
        ensureGeneralCategory();
        List<ProductCategory> categories = productCategoryRepository.findByIsActiveTrueOrderByLabel();
        List<ProductCategoryDTO> dtos = categories.stream()
                .map(c -> ProductCategoryDTO.builder()
                        .code(c.getCode())
                        .label(c.getLabel())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @Operation(summary = "Créer une catégorie", description = "Crée une nouvelle catégorie de produit (GENERAL reste toujours disponible)")
    public ResponseEntity<ProductCategoryDTO> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        ensureGeneralCategory();

        String normalizedLabel = request.getLabel().trim();
        if (productCategoryRepository.findByLabelIgnoreCase(normalizedLabel).isPresent()) {
            throw new RuntimeException("Une catégorie existe déjà avec ce libellé");
        }

        String code = normalizeCode(request.getCode());
        if (code.isBlank()) {
            throw new RuntimeException("Le code de la catégorie est invalide");
        }
        if (productCategoryRepository.existsById(code)) {
            throw new RuntimeException("Une catégorie existe déjà avec ce code");
        }

        ProductCategory category = new ProductCategory();
        category.setCode(code);
        category.setLabel(normalizedLabel);
        category.setDescription("Catégorie créée depuis l'interface");
        category.setIsActive(true);
        ProductCategory saved = productCategoryRepository.save(category);

        return ResponseEntity.status(201).body(ProductCategoryDTO.builder()
                .code(saved.getCode())
                .label(saved.getLabel())
                .build());
    }

    private void ensureGeneralCategory() {
        productCategoryRepository.findById(GENERAL_CODE).ifPresentOrElse(cat -> {
            if (!Boolean.TRUE.equals(cat.getIsActive())) {
                cat.setIsActive(true);
                if (cat.getLabel() == null || cat.getLabel().isBlank()) {
                    cat.setLabel("GENERAL");
                }
                productCategoryRepository.save(cat);
            }
        }, () -> {
            ProductCategory general = new ProductCategory();
            general.setCode(GENERAL_CODE);
            general.setLabel("GENERAL");
            general.setDescription("Catégorie générique par défaut");
            general.setIsActive(true);
            productCategoryRepository.save(general);
        });
    }

    private String normalizeCode(String raw) {
        return raw.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]", "_")
                .replaceAll("_+", "_");
    }
}
