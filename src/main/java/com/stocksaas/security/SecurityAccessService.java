package com.stocksaas.security;

import com.stocksaas.exception.ForbiddenAccessException;
import com.stocksaas.model.Company;
import com.stocksaas.model.User;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.repository.UserWarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Contrôles d'accès centralisés (rôles, isolation par entreprise).
 */
@Service
@RequiredArgsConstructor
public class SecurityAccessService {

    private final UserRepository userRepository;
    private final UserWarehouseRepository userWarehouseRepository;

    public User requireAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ForbiddenAccessException("Non authentifié");
        }
        return userRepository.findByEmailWithCompanyAndRole(auth.getName())
                .orElseThrow(() -> new ForbiddenAccessException("Utilisateur non trouvé"));
    }

    public User requireSuperAdmin() {
        User user = requireAuthenticatedUser();
        if (!user.isSuperAdmin()) {
            throw new ForbiddenAccessException("Accès réservé au super administrateur");
        }
        return user;
    }

    public User requireAdminEntreprise() {
        User user = requireAuthenticatedUser();
        if (!user.isAdminEntreprise()) {
            throw new ForbiddenAccessException("Accès réservé à l'administrateur entreprise");
        }
        return user;
    }

    public User requireAdminEntrepriseOrSuperAdmin() {
        User user = requireAuthenticatedUser();
        if (!user.isSuperAdmin() && !user.isAdminEntreprise()) {
            throw new ForbiddenAccessException("Accès non autorisé");
        }
        return user;
    }

    public Long requireCompanyId(User user) {
        if (user.getCompany() == null) {
            throw new ForbiddenAccessException("Aucune entreprise associée");
        }
        return user.getCompany().getId();
    }

    public void assertSameCompany(User actor, Long resourceCompanyId) {
        if (actor.isSuperAdmin()) {
            return;
        }
        Long actorCompanyId = requireCompanyId(actor);
        if (resourceCompanyId == null || !actorCompanyId.equals(resourceCompanyId)) {
            throw new ForbiddenAccessException("Accès non autorisé à cette ressource");
        }
    }

    public void assertSameCompany(User actor, Company resourceCompany) {
        assertSameCompany(actor, resourceCompany != null ? resourceCompany.getId() : null);
    }

    public void assertSameCompany(User actor, User target) {
        if (target.getCompany() == null) {
            throw new ForbiddenAccessException("Accès non autorisé à cet utilisateur");
        }
        assertSameCompany(actor, target.getCompany().getId());
    }

    public void assertCanManageUser(User actor, User target) {
        if (target.isSuperAdmin()) {
            throw new ForbiddenAccessException("Les utilisateurs super administrateur ne sont pas modifiables");
        }
        if (actor.isSuperAdmin()) {
            return;
        }
        if (!actor.isAdminEntreprise()) {
            throw new ForbiddenAccessException("Accès non autorisé");
        }
        assertSameCompany(actor, target);
        if (!target.isGestionnaire()) {
            throw new ForbiddenAccessException("Vous ne pouvez gérer que les gestionnaires de votre entreprise");
        }
    }

    public List<Long> getAssignedWarehouseIds(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }
        return userWarehouseRepository.findWarehouseIdsByUserId(user.getId());
    }

    public void assertWarehouseAccessible(User user, Long warehouseId) {
        if (user.isGestionnaire()) {
            List<Long> allowed = getAssignedWarehouseIds(user);
            if (warehouseId == null || !allowed.contains(warehouseId)) {
                throw new ForbiddenAccessException("Accès non autorisé à cet entrepôt");
            }
        }
    }
}
