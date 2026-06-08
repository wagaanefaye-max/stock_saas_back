package com.stocksaas.service;

import com.stocksaas.exception.ForbiddenAccessException;
import com.stocksaas.dto.PageResponse;
import com.stocksaas.dto.UpdateUserRequest;
import com.stocksaas.dto.UpdateUserStatusRequest;
import com.stocksaas.dto.UserDTO;
import com.stocksaas.model.Company;
import com.stocksaas.model.User;
import com.stocksaas.model.UserRole;
import com.stocksaas.repository.CompanyRepository;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.repository.UserRoleRepository;
import com.stocksaas.security.SecurityAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service pour la gestion des utilisateurs
 */
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final CompanyRepository companyRepository;
    private final SecurityAccessService securityAccessService;
    
    /**
     * Liste paginée selon le rôle de l'appelant (super-admin : tous ; admin entreprise : gestionnaires de son entreprise).
     */
    @Transactional(readOnly = true)
    public PageResponse<UserDTO> listUsersForCurrentUser(int page, int size, String search) {
        User actor = securityAccessService.requireAdminEntrepriseOrSuperAdmin();
        if (actor.isSuperAdmin()) {
            return getAllUsersExceptSuperAdmin(page, size, search);
        }
        return getGestionnairesByCompany(securityAccessService.requireCompanyId(actor), page, size, search);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDTO> getGestionnairesByCompany(Long companyId, int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> userPage = (search != null && !search.trim().isEmpty())
                ? userRepository.findGestionnairesByCompanyIdWithSearch(companyId, search.trim(), pageable)
                : userRepository.findGestionnairesByCompanyId(companyId, pageable);
        return toPageResponse(userPage);
    }
    
    /**
     * Récupère tous les utilisateurs sauf les SUPER_ADMIN avec pagination
     */
    @Transactional(readOnly = true)
    public PageResponse<UserDTO> getAllUsersExceptSuperAdmin(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> userPage;
        
        if (search != null && !search.trim().isEmpty()) {
            userPage = userRepository.findAllExceptSuperAdminWithSearch(search.trim(), pageable);
        } else {
            userPage = userRepository.findAllExceptSuperAdmin(pageable);
        }
        
        return toPageResponse(userPage);
    }

    private PageResponse<UserDTO> toPageResponse(Page<User> userPage) {
        return PageResponse.<UserDTO>builder()
                .content(userPage.getContent().stream()
                        .map(this::mapToDTO)
                        .toList())
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .build();
    }
    
    /**
     * Récupère un utilisateur par son ID
     */
    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        User actor = securityAccessService.requireAdminEntrepriseOrSuperAdmin();
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé avec l'ID: " + id));
        
        if (user.isSuperAdmin()) {
            throw new RuntimeException("Les utilisateurs SUPER_ADMIN ne peuvent pas être consultés via cette API");
        }
        securityAccessService.assertCanManageUser(actor, user);
        
        return mapToDTO(user);
    }
    
    /**
     * Met à jour un utilisateur
     */
    @Transactional
    public UserDTO updateUser(Long id, UpdateUserRequest request) {
        User actor = securityAccessService.requireAdminEntrepriseOrSuperAdmin();
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé avec l'ID: " + id));
        
        securityAccessService.assertCanManageUser(actor, user);
        
        if (actor.isAdminEntreprise()) {
            if (request.getCompanyId() != null
                    && !request.getCompanyId().equals(securityAccessService.requireCompanyId(actor))) {
                throw new ForbiddenAccessException("Vous ne pouvez pas affecter un utilisateur à une autre entreprise");
            }
            if (request.getRoleCode() != null && !request.getRoleCode().isEmpty()
                    && !"GESTIONNAIRE".equals(request.getRoleCode())) {
                throw new ForbiddenAccessException("Vous ne pouvez assigner que le rôle Gestionnaire");
            }
        }
        
        // Vérifier si l'email est déjà utilisé par un autre utilisateur
        if (!user.getEmail().equals(request.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Un compte existe déjà avec cet email");
            }
        }
        
        // Mettre à jour les champs
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        
        // Mettre à jour le rôle si fourni
        if (request.getRoleCode() != null && !request.getRoleCode().isEmpty()) {
            UserRole role = userRoleRepository.findById(request.getRoleCode())
                    .orElseThrow(() -> new RuntimeException("Rôle non trouvé: " + request.getRoleCode()));
            // Ne pas permettre de changer vers SUPER_ADMIN
            if ("SUPER_ADMIN".equals(role.getCode())) {
                throw new RuntimeException("Le rôle SUPER_ADMIN ne peut pas être assigné via cette API");
            }
            user.setRole(role);
        }
        
        // Mettre à jour le statut si fourni
        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            user.setStatus(request.getStatus());
        }
        
        // Mettre à jour l'entreprise si fournie (super-admin uniquement)
        if (request.getCompanyId() != null && actor.isSuperAdmin()) {
            Company company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new RuntimeException("Entreprise non trouvée avec l'ID: " + request.getCompanyId()));
            user.setCompany(company);
        }
        
        user = userRepository.save(user);
        return mapToDTO(user);
    }
    
    /**
     * Met à jour le statut d'un utilisateur
     */
    @Transactional
    public UserDTO updateUserStatus(Long id, UpdateUserStatusRequest request) {
        User actor = securityAccessService.requireAdminEntrepriseOrSuperAdmin();
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé avec l'ID: " + id));
        
        securityAccessService.assertCanManageUser(actor, user);
        
        user.setStatus(request.getStatus());
        user = userRepository.save(user);
        return mapToDTO(user);
    }
    
    /**
     * Supprime un utilisateur (soft delete)
     */
    @Transactional
    public void deleteUser(Long id) {
        User actor = securityAccessService.requireAdminEntrepriseOrSuperAdmin();
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé avec l'ID: " + id));
        
        securityAccessService.assertCanManageUser(actor, user);
        
        // Soft delete
        user.setIsDeleted(true);
        userRepository.save(user);
    }
    
    /**
     * Mappe un User vers un UserDTO
     */
    private UserDTO mapToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .roleCode(user.getRole() != null ? user.getRole().getCode() : null)
                .roleLabel(user.getRole() != null ? user.getRole().getLabel() : null)
                .status(user.getStatus())
                .companyId(user.getCompany() != null ? user.getCompany().getId() : null)
                .companyName(user.getCompany() != null ? user.getCompany().getName() : null)
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
