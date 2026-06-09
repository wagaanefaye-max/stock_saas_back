package com.stocksaas.controller;

import com.stocksaas.dto.CreatePartnerRequest;
import com.stocksaas.dto.PartnerDTO;
import com.stocksaas.dto.UpdatePartnerRequest;
import com.stocksaas.model.User;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
@Tag(name = "Partenaires", description = "Clients et fournisseurs de l'entreprise (sans compte)")
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
public class PartnerController {

    private final PartnerService partnerService;
    private final UserRepository userRepository;

    private Long getCompanyId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("Non authentifié");
        }
        User user = userRepository.findByEmailWithCompanyAndRole(auth.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (user.getCompany() == null) {
            throw new RuntimeException("Aucune entreprise associée");
        }
        return user.getCompany().getId();
    }

    @PostMapping
    @Operation(summary = "Créer un partenaire (client ou fournisseur)")
    public ResponseEntity<?> create(@Valid @RequestBody CreatePartnerRequest request) {
        try {
            Long companyId = getCompanyId();
            PartnerDTO dto = partnerService.create(companyId, request);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Liste des partenaires (optionnel: role=CLIENT ou FOURNISSEUR; page/size pour pagination; search=nom ou email)")
    public ResponseEntity<?> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search) {
        try {
            Long companyId = getCompanyId();
            if (page != null && size != null && size > 0) {
                var paged = partnerService.findAllByCompanyPaged(companyId, role, page, size, search);
                Map<String, Object> body = new HashMap<>();
                body.put("content", paged.getContent());
                body.put("totalElements", paged.getTotalElements());
                return ResponseEntity.ok(body);
            }
            List<PartnerDTO> list = partnerService.findAllByCompany(companyId, role);
            return ResponseEntity.ok(list);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un partenaire")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            Long companyId = getCompanyId();
            return ResponseEntity.ok(partnerService.getById(companyId, id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un partenaire")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody UpdatePartnerRequest request) {
        try {
            Long companyId = getCompanyId();
            return ResponseEntity.ok(partnerService.update(companyId, id, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer (soft) un partenaire")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            Long companyId = getCompanyId();
            partnerService.delete(companyId, id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
