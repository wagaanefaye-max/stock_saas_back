package com.stocksaas.service;

import com.stocksaas.dto.AuthResponse;
import com.stocksaas.dto.CreateCompanyRequest;
import com.stocksaas.dto.LoginRequest;
import com.stocksaas.dto.RegisterRequest;
import com.stocksaas.dto.RegisterResponse;
import com.stocksaas.dto.VerifyAccountRequest;
import com.stocksaas.model.Company;
import com.stocksaas.model.CompanyStatus;
import com.stocksaas.model.User;
import com.stocksaas.model.UserVerificationToken;
import com.stocksaas.model.Warehouse;
import com.stocksaas.model.WarehouseStatus;
import com.stocksaas.model.SubscriptionPlan;
import com.stocksaas.model.UserRole;
import com.stocksaas.repository.CompanyRepository;
import com.stocksaas.repository.CompanyStatusRepository;
import com.stocksaas.repository.SubscriptionPlanRepository;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.repository.UserRoleRepository;
import com.stocksaas.repository.UserVerificationTokenRepository;
import com.stocksaas.repository.WarehouseRepository;
import com.stocksaas.repository.WarehouseStatusRepository;
import com.stocksaas.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Service pour l'authentification
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyStatusRepository companyStatusRepository;
    private final UserRoleRepository userRoleRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserVerificationTokenRepository verificationTokenRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStatusRepository warehouseStatusRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final SubscriptionService subscriptionService;
    
    /**
     * Domaines d'emails jetables à refuser (yopmail, mailinator, etc.).
     */
    private static final Set<String> DISPOSABLE_EMAIL_DOMAINS = Set.of(
            "yopmail.com",
            "yopmail.fr",
            "mailinator.com",
            "mailinator.net",
            "mailinator.org",
            "tempmail.com",
            "10minutemail.com",
            "guerrillamail.com",
            "trashmail.com"
    );

    /**
     * Authentifie un utilisateur et retourne un token JWT
     */
    public AuthResponse login(LoginRequest request) {
        // Vérifier d'abord si l'utilisateur existe et a un mot de passe
        User user = userRepository.findByEmailWithCompanyAndRole(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        // Vérifier si le compte est validé (a un mot de passe)
        if (user.getPassword() == null) {
            throw new RuntimeException("Votre compte n'est pas encore activé. Veuillez vérifier votre email et cliquer sur le lien de validation.");
        }
        
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        // Mettre à jour la date de dernière connexion
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        
        // Envoyer un email de notification de connexion
        emailService.sendLoginNotification(user.getEmail(), user.getName());
        
        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole() != null ? user.getRole().getCode() : null,
                user.getId(),
                user.getCompany() != null ? user.getCompany().getId() : null
        );
        
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole() != null ? user.getRole().getCode() : null)
                .companyId(user.getCompany() != null ? user.getCompany().getId() : null)
                .companyName(user.getCompany() != null ? user.getCompany().getName() : null)
                .build();
        subscriptionService.enrichAuthResponse(response, user);
        return response;
    }
    
    /**
     * Inscrit un nouvel utilisateur avec création d'entreprise
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Refuser les adresses jetables (yopmail, mailinator, etc.)
        validateEmailNotDisposable(request.getEmail());

        // Vérifier si l'email existe déjà
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Un compte existe déjà avec cet email");
        }
        
        // Récupérer le plan d'abonnement sélectionné (ou "Free" par défaut)
        String planCode = request.getPlanCode() != null ? request.getPlanCode() : "Free";
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planCode)
                .orElseGet(() -> {
                    // Si le plan n'existe pas, utiliser "Free" par défaut
                    return subscriptionPlanRepository.findById("Free")
                            .orElseThrow(() -> new RuntimeException("Plan d'abonnement 'Free' non trouvé. Veuillez exécuter le DataSeeder."));
                });
        
        // Créer l'entreprise
        Company company = new Company();
        company.setName(request.getCompanyName() != null ? request.getCompanyName() : request.getName() + " - Entreprise");
        company.setEmail(request.getCompanyEmail() != null ? request.getCompanyEmail() : request.getEmail());
        company.setPhone(request.getCompanyPhone());
        company.setAddress(request.getCompanyAddress());
        company.setRegion(request.getCompanyRegion());
        company.setCountry("Sénégal");
        company.setPlan(plan);
        // Récupérer le statut Actif depuis la table TP (code = "Actif" créé par le DataSeeder)
        CompanyStatus activeStatus = companyStatusRepository.findById("Actif")
                .or(()-> companyStatusRepository.findById("ACTIF"))
                .orElseThrow(() -> new RuntimeException("Statut Actif non trouvé. Veuillez exécuter le DataSeeder."));
        company.setStatus(activeStatus);
        company.setIsDeleted(false);
        subscriptionService.initializeTrialForNewCompany(company);
        company = companyRepository.save(company);

        // Créer un entrepôt par défaut pour la nouvelle entreprise
        WarehouseStatus warehouseActiveStatus = warehouseStatusRepository.findById("Actif")
                .orElseGet(() -> warehouseStatusRepository.findById("ACTIF").orElse(null));
        if (warehouseActiveStatus != null) {
            Warehouse defaultWarehouse = new Warehouse();
            defaultWarehouse.setCompany(company);
            defaultWarehouse.setName("DEFAULT-ENTREPOT");
            defaultWarehouse.setRegion(company.getRegion() != null && !company.getRegion().isBlank() ? company.getRegion() : "Défaut");
            defaultWarehouse.setDescription("Entrepôt par défaut");
            defaultWarehouse.setStatus(warehouseActiveStatus);
            defaultWarehouse.setIsDeleted(false);
            warehouseRepository.save(defaultWarehouse);
        }
        
        // Récupérer le rôle Admin Entreprise
        UserRole adminRole = userRoleRepository.findById("ADMIN_ENTREPRISE")
                .orElseThrow(() -> new RuntimeException("Rôle ADMIN_ENTREPRISE non trouvé"));
        
        // Créer l'utilisateur (sans mot de passe pour l'instant)
        User user = new User();
        user.setCompany(company);
        user.setEmail(request.getEmail());
        user.setPassword(null); // Le mot de passe sera défini lors de la validation
        user.setName(request.getName());
        user.setRole(adminRole);
        user.setStatus("Inactif"); // Inactif jusqu'à validation du compte
        user.setIsDeleted(false);
        user = userRepository.save(user);
        
        // Générer un token de réinitialisation / activation (valide 2 jours)
        String verificationToken = generateVerificationToken();
        UserVerificationToken tokenEntity = new UserVerificationToken();
        tokenEntity.setToken(verificationToken);
        tokenEntity.setUser(user);
        tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(2));
        tokenEntity.setUsed(false);
        verificationTokenRepository.save(tokenEntity);
        
        // Envoyer un email avec lien de réinitialisation (définir mot de passe)
        emailService.sendAccountVerificationEmail(user.getEmail(), user.getName(), company.getName(), verificationToken);
        
        // Retourner une réponse de succès (sans token JWT, l'utilisateur doit d'abord valider son compte)
        return RegisterResponse.builder()
                .message("Un email a été envoyé avec un lien de réinitialisation (valide 2 jours). Cliquez sur le lien pour définir votre mot de passe puis connectez-vous.")
                .email(user.getEmail())
                .companyName(company.getName())
                .build();
    }

    /**
     * Création d'une boutique (entreprise) par le Super Admin.
     * Crée l'entreprise, un entrepôt par défaut, un utilisateur Admin Entreprise sans mot de passe,
     * puis envoie un email d'activation à l'administrateur pour qu'il définisse son mot de passe.
     */
    @Transactional
    public Long createCompanyBySuperAdmin(CreateCompanyRequest request) {
        validateEmailNotDisposable(request.getAdminEmail());

        if (companyRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Une entreprise existe déjà avec cet email");
        }
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new RuntimeException("Un compte existe déjà avec l'email administrateur indiqué");
        }

        String planCodeRaw = request.getPlanCode() != null && !request.getPlanCode().isBlank() ? request.getPlanCode().trim() : "Free";
        String planCode = normalizePlanCode(planCodeRaw);
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planCode)
                .orElseGet(() -> subscriptionPlanRepository.findById("Free")
                        .orElseThrow(() -> new RuntimeException("Plan d'abonnement 'Free' non trouvé. Veuillez exécuter le DataSeeder.")));

        Company company = new Company();
        company.setName(request.getName());
        company.setEmail(request.getEmail());
        company.setPhone(request.getPhone());
        company.setAddress(request.getAddress());
        company.setRegion(request.getRegion());
        company.setCountry(request.getCountry() != null && !request.getCountry().isBlank() ? request.getCountry() : "Sénégal");
        company.setPlan(plan);
        CompanyStatus activeStatus = companyStatusRepository.findById("Actif")
                .or(() -> companyStatusRepository.findById("ACTIF"))
                .orElseThrow(() -> new RuntimeException("Statut Actif non trouvé. Veuillez exécuter le DataSeeder."));
        company.setStatus(activeStatus);
        company.setIsDeleted(false);
        subscriptionService.initializeTrialForNewCompany(company);
        company = companyRepository.save(company);

        WarehouseStatus warehouseActiveStatus = warehouseStatusRepository.findById("Actif")
                .orElseGet(() -> warehouseStatusRepository.findById("ACTIF").orElse(null));
        if (warehouseActiveStatus != null) {
            Warehouse defaultWarehouse = new Warehouse();
            defaultWarehouse.setCompany(company);
            defaultWarehouse.setName("DEFAULT-ENTREPOT");
            defaultWarehouse.setRegion(company.getRegion() != null && !company.getRegion().isBlank() ? company.getRegion() : "Défaut");
            defaultWarehouse.setDescription("Entrepôt par défaut");
            defaultWarehouse.setStatus(warehouseActiveStatus);
            defaultWarehouse.setIsDeleted(false);
            warehouseRepository.save(defaultWarehouse);
        }

        UserRole adminRole = userRoleRepository.findById("ADMIN_ENTREPRISE")
                .orElseThrow(() -> new RuntimeException("Rôle ADMIN_ENTREPRISE non trouvé"));

        String adminName = (request.getAdminFirstName() + " " + request.getAdminLastName()).trim();
        if (adminName.isBlank()) {
            adminName = request.getAdminEmail();
        }

        User user = new User();
        user.setCompany(company);
        user.setEmail(request.getAdminEmail());
        user.setPassword(null);
        user.setName(adminName);
        user.setRole(adminRole);
        user.setStatus("Inactif");
        user.setIsDeleted(false);
        user = userRepository.save(user);

        String verificationToken = generateVerificationToken();
        UserVerificationToken tokenEntity = new UserVerificationToken();
        tokenEntity.setToken(verificationToken);
        tokenEntity.setUser(user);
        tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(2));
        tokenEntity.setUsed(false);
        verificationTokenRepository.save(tokenEntity);

        emailService.sendAccountVerificationEmail(user.getEmail(), user.getName(), company.getName(), verificationToken);

        return company.getId();
    }

    /**
     * Demande de réinitialisation de mot de passe pour un compte existant.
     * Un email avec un lien (même page que la validation de compte) est envoyé.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmailAndNotDeleted(email)
                .orElseThrow(() -> new RuntimeException("Aucun compte trouvé avec cet email"));

        if (user.getPassword() == null) {
            throw new RuntimeException("Votre compte n'est pas encore activé. Veuillez utiliser le lien de validation initial.");
        }

        String verificationToken = generateVerificationToken();
        UserVerificationToken tokenEntity = new UserVerificationToken();
        tokenEntity.setToken(verificationToken);
        tokenEntity.setUser(user);
        tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(2));
        tokenEntity.setUsed(false);
        verificationTokenRepository.save(tokenEntity);

        String companyName = user.getCompany() != null ? user.getCompany().getName() : "Stock SaaS";
        emailService.sendAccountVerificationEmail(user.getEmail(), user.getName(), companyName, verificationToken);
    }
    
    /**
     * Valide le compte et définit le mot de passe (lien cliqué dans l'email)
     */
    @Transactional
    public AuthResponse verifyAccount(VerifyAccountRequest request) {
        if (request.getPasswordConfirmation() != null && !request.getPassword().equals(request.getPasswordConfirmation())) {
            throw new RuntimeException("Le mot de passe et la confirmation ne correspondent pas");
        }
        // Trouver le token de validation
        UserVerificationToken tokenEntity = verificationTokenRepository.findValidTokenWithUser(
                request.getToken(), 
                LocalDateTime.now()
        ).orElseThrow(() -> new RuntimeException("Token de validation invalide ou expiré"));
        
        // Vérifier que le token n'a pas déjà été utilisé
        if (tokenEntity.getUsed()) {
            throw new RuntimeException("Ce lien de validation a déjà été utilisé");
        }
        
        // Récupérer l'utilisateur
        User user = tokenEntity.getUser();
        
        // Définir le mot de passe
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus("Actif");
        userRepository.save(user);
        
        // Marquer le token comme utilisé
        verificationTokenRepository.markAsUsed(request.getToken());

        user = userRepository.findByEmailWithCompanyAndRole(user.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        // Générer le token JWT (l'utilisateur peut être SUPER_ADMIN sans entreprise)
        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        String jwtToken = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().getCode(),
                user.getId(),
                companyId
        );
        
        // Envoyer un email de confirmation
        emailService.sendAccountActivatedEmail(user.getEmail(), user.getName());
        
        AuthResponse response = AuthResponse.builder()
                .token(jwtToken)
                .type("Bearer")
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().getCode())
                .companyId(companyId)
                .companyName(user.getCompany() != null ? user.getCompany().getName() : null)
                .build();
        subscriptionService.enrichAuthResponse(response, user);
        return response;
    }
    
    /**
     * Génère un token de validation unique
     */
    private String generateVerificationToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Normalise le code plan (ex: FREE -> Free) pour correspondre au DataSeeder.
     */
    private static String normalizePlanCode(String code) {
        if (code == null || code.isBlank()) return "Free";
        switch (code.toUpperCase()) {
            case "FREE": return "Free";
            case "BASIQUE": return "Basique";
            case "STANDARD": return "Standard";
            case "PREMIUM": return "Premium";
            default: return code;
        }
    }

    /**
     * Vérifie que l'email n'appartient pas à un domaine jetable.
     */
    private void validateEmailNotDisposable(String email) {
        if (email == null || !email.contains("@")) {
            return;
        }
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase().trim();
        if (DISPOSABLE_EMAIL_DOMAINS.contains(domain)) {
            throw new RuntimeException("Les adresses e-mail jetables (yopmail, mailinator, etc.) ne sont pas acceptées. Merci d'utiliser une adresse professionnelle.");
        }
    }
}
