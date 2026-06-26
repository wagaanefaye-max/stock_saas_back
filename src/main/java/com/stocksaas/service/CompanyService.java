package com.stocksaas.service;

import com.stocksaas.dto.CompanyDTO;
import com.stocksaas.dto.PageResponse;
import com.stocksaas.dto.ProofResourceResult;
import com.stocksaas.dto.UpdateCompanyRequest;
import com.stocksaas.dto.UpdateCompanyStatusRequest;
import com.stocksaas.exception.ProofNotFoundException;
import com.stocksaas.model.Company;
import com.stocksaas.model.CompanyStatus;
import com.stocksaas.model.User;
import com.stocksaas.model.SubscriptionPlan;
import com.stocksaas.repository.CompanyRepository;
import com.stocksaas.repository.CompanyStatusRepository;
import com.stocksaas.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Service pour la gestion des entreprises
 */
@Service
@RequiredArgsConstructor
public class CompanyService {
    
    private final CompanyRepository companyRepository;
    private final CompanyStatusRepository companyStatusRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CompanyLogoStorageService companyLogoStorageService;
    
    /**
     * Récupère toutes les entreprises avec pagination (DTO retournés directement par le repository, une requête).
     */
    @Transactional(readOnly = true)
    public PageResponse<CompanyDTO> getAllCompanies(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<CompanyDTO> dtoPage = (search != null && !search.trim().isEmpty())
                ? companyRepository.findAllNotDeletedWithSearchAsDTO(search.trim(), pageable)
                : companyRepository.findAllNotDeletedAsDTO(pageable);
        return PageResponse.<CompanyDTO>builder()
                .content(dtoPage.getContent())
                .page(dtoPage.getNumber())
                .size(dtoPage.getSize())
                .totalElements(dtoPage.getTotalElements())
                .totalPages(dtoPage.getTotalPages())
                .first(dtoPage.isFirst())
                .last(dtoPage.isLast())
                .build();
    }
    
    /**
     * Récupère une entreprise par son ID (DTO retourné directement par le repository, une requête).
     */
    @Transactional(readOnly = true)
    public CompanyDTO getCompanyById(Long id) {
        return companyRepository.findDTOById(id)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée avec l'ID: " + id));
    }
    
    /**
     * Met à jour une entreprise
     */
    @Transactional
    public CompanyDTO updateCompany(Long id, UpdateCompanyRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée avec l'ID: " + id));
        
        if (company.getIsDeleted()) {
            throw new RuntimeException("L'entreprise a été supprimée");
        }
        
        // Vérifier si l'email est déjà utilisé par une autre entreprise
        if (!company.getEmail().equals(request.getEmail())) {
            if (companyRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("Une entreprise existe déjà avec cet email");
            }
        }
        
        // Mettre à jour les champs
        company.setName(request.getName());
        company.setEmail(request.getEmail());
        company.setPhone(request.getPhone());
        company.setAddress(request.getAddress());
        company.setRegion(request.getRegion());
        if (request.getCountry() != null) {
            company.setCountry(request.getCountry());
        }
        if (request.getNotifLowStock() != null) {
            company.setNotifLowStock(request.getNotifLowStock());
        }
        if (request.getNotifMovements() != null) {
            company.setNotifMovements(request.getNotifMovements());
        }
        if (request.getNotifReports() != null) {
            company.setNotifReports(request.getNotifReports());
        }
        
        // Mettre à jour le plan d'abonnement si fourni
        if (request.getPlanCode() != null && !request.getPlanCode().isEmpty()) {
            SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getPlanCode())
                    .orElseThrow(() -> new RuntimeException("Plan d'abonnement non trouvé: " + request.getPlanCode()));
            company.setPlan(plan);
        }
        
        return mapToDTO(companyRepository.save(company));
    }

    @Transactional
    public CompanyDTO uploadCompanyLogo(Long id, MultipartFile image) throws IOException {
        Company company = getActiveCompany(id);
        var stored = companyLogoStorageService.storeLogo(id, image);
        company.setLogoUrl(buildLogoApiUrl(id));
        company.setLogoData(stored.data());
        company.setLogoContentType(stored.contentType());
        return mapToDTO(companyRepository.save(company));
    }

    @Transactional
    public CompanyDTO deleteCompanyLogo(Long id) throws IOException {
        Company company = getActiveCompany(id);
        companyLogoStorageService.deleteLogo(id);
        company.setLogoUrl(null);
        company.setLogoData(null);
        company.setLogoContentType(null);
        return mapToDTO(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public ProofResourceResult getCompanyLogo(Long id) throws IOException {
        Company company = getActiveCompany(id);
        if (company.getLogoUrl() == null || company.getLogoUrl().isBlank()) {
            throw new ProofNotFoundException("Logo non disponible pour cette entreprise");
        }
        return companyLogoStorageService.loadLogo(company);
    }

    private Company getActiveCompany(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée avec l'ID: " + id));
        if (company.getIsDeleted()) {
            throw new RuntimeException("L'entreprise a été supprimée");
        }
        return company;
    }

    private static String buildLogoApiUrl(Long companyId) {
        return "/api/companies/" + companyId + "/logo";
    }
    
    /**
     * Met à jour le statut d'une entreprise
     */
    @Transactional
    public CompanyDTO updateCompanyStatus(Long id, UpdateCompanyStatusRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée avec l'ID: " + id));
        
        if (company.getIsDeleted()) {
            throw new RuntimeException("L'entreprise a été supprimée");
        }
        
        // Normaliser vers les codes référentiel (Actif, Inactif, Suspendu)
        String statusCodeInput = request.getStatus();
        if (statusCodeInput != null) {
            final String statusCode;
            String normalized = statusCodeInput.trim();
            if (normalized.equalsIgnoreCase("Actif") || normalized.equalsIgnoreCase("ACTIF")) {
                statusCode = "Actif";
            } else if (normalized.equalsIgnoreCase("Inactif") || normalized.equalsIgnoreCase("INACTIF")) {
                statusCode = "Inactif";
            } else if (normalized.equalsIgnoreCase("Suspendu") || normalized.equalsIgnoreCase("SUSPENDU")) {
                statusCode = "Suspendu";
            } else {
                statusCode = normalized;
            }

            CompanyStatus status = companyStatusRepository.findById(statusCode)
                    .orElseThrow(() -> new RuntimeException("Statut non trouvé: " + statusCode));
            company.setStatus(status);
        }
        
        return mapToDTO(companyRepository.save(company));
    }
    
    /**
     * Supprime logiquement une entreprise (soft delete)
     */
    @Transactional
    public void deleteCompany(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée avec l'ID: " + id));
        
        if (company.getIsDeleted()) {
            throw new RuntimeException("L'entreprise a déjà été supprimée");
        }
        
        company.setIsDeleted(true);
        companyRepository.save(company);
    }
    
    /**
     * Mappe un Company vers un CompanyDTO (détail : company.getUsers() déjà chargé).
     */
    private CompanyDTO mapToDTO(Company company) {
        List<User> users = company.getUsers() != null ? company.getUsers() : List.of();
        return mapToDTOWithUsers(company, users);
    }
    
    /**
     * Mappe un Company vers un CompanyDTO avec liste d'utilisateurs fournie (liste : évite lazy load).
     */
    private CompanyDTO mapToDTO(Company company, List<User> usersForCompany) {
        return mapToDTOWithUsers(company, usersForCompany != null ? usersForCompany : List.of());
    }
    
    private CompanyDTO mapToDTOWithUsers(Company company, List<User> usersList) {
        long userCount = usersList.stream().filter(user -> !user.getIsDeleted()).count();
        var adminOpt = usersList.stream()
                .filter(user -> !user.getIsDeleted())
                .filter(user -> user.getRole() != null && "ADMIN_ENTREPRISE".equals(user.getRole().getCode()))
                .findFirst();
        String adminName = adminOpt.map(User::getName).orElse(null);
        String adminEmail = adminOpt.map(User::getEmail).orElse(null);
        
        return CompanyDTO.builder()
                .id(company.getId())
                .name(company.getName())
                .email(company.getEmail())
                .phone(company.getPhone())
                .address(company.getAddress())
                .region(company.getRegion())
                .country(company.getCountry())
                .planCode(company.getPlan() != null ? company.getPlan().getCode() : null)
                .planLabel(company.getPlan() != null ? company.getPlan().getLabel() : null)
                .statusCode(company.getStatus() != null ? company.getStatus().getCode() : null)
                .statusLabel(company.getStatus() != null ? company.getStatus().getLabel() : null)
                .logoUrl(company.getLogoUrl())
                .userCount(userCount)
                .createdAt(company.getCreatedAt())
                .adminName(adminName)
                .adminEmail(adminEmail)
                .notifLowStock(company.getNotifLowStock())
                .notifMovements(company.getNotifMovements())
                .notifReports(company.getNotifReports())
                .subscriptionStatus(company.getSubscriptionStatus())
                .trialEndsAt(company.getTrialEndsAt())
                .subscriptionEndsAt(company.getSubscriptionEndsAt())
                .durationCode(company.getDurationCode())
                .build();
    }
}
