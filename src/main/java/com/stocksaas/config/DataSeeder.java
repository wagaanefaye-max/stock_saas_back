package com.stocksaas.config;

import com.stocksaas.model.*;
import com.stocksaas.repository.*;
import com.stocksaas.repository.CompanySubscriptionRecordRepository;
import com.stocksaas.service.SubscriptionService;
import com.stocksaas.subscription.SubscriptionRequestStatusCode;
import com.stocksaas.subscription.SubscriptionStatusCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DataSeeder pour initialiser les données de référence et un super admin au démarrage
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {
    
    private final UserRoleRepository userRoleRepository;
    private final CompanyStatusRepository companyStatusRepository;
    private final WarehouseStatusRepository warehouseStatusRepository;
    private final ProductStatusRepository productStatusRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final MovementTypeRepository movementTypeRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserWarehouseRepository userWarehouseRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionDurationRepository subscriptionDurationRepository;
    private final SubscriptionService subscriptionService;
    private final CompanySubscriptionRecordRepository subscriptionRecordRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    
    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Démarrage du DataSeeder...");
        
        // Créer les données de référence
        seedReferenceData();
        
        // Rétroactiver l'essai pour les entreprises existantes
        backfillCompanySubscriptions();
        backfillSubscriptionRecordsStatus();

        // Créer les utilisateurs de test pour chaque profil
        seedTestUsers();
        
        log.info("DataSeeder terminé avec succès !");
    }
    
    /**
     * Crée les données de référence (tables TP)
     */
    private void seedReferenceData() {
        log.info("Création des données de référence...");
        
        seedPlatformSettings();

        // Plans d'abonnement
        seedSubscriptionPlans();

        // Durées de facturation (1, 3, 6, 12 mois)
        seedSubscriptionDurations();
        
        // Rôles utilisateurs
        seedUserRoles();
        
        // Statuts entreprise
        seedCompanyStatuses();
        
        // Statuts entrepôt
        seedWarehouseStatuses();
        
        // Statuts produit
        seedProductStatuses();
        
        // Catégories de produits
        seedProductCategories();
        
        // Types de mouvement
        seedMovementTypes();
        
        log.info("Données de référence créées avec succès !");
    }
    
    private void seedPlatformSettings() {
        if (platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID).isEmpty()) {
            PlatformSettings settings = new PlatformSettings();
            settings.setId(PlatformSettings.SINGLETON_ID);
            settings.setSubscriptionMonthlyPriceFcfa(5000.0);
            settings.setMaintenanceMode(false);
            settings.setAllowNewRegistrations(true);
            platformSettingsRepository.save(settings);
            log.debug("Paramètres plateforme initialisés");
        }
    }

    private double defaultMonthlyPrice() {
        return platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)
                .map(PlatformSettings::getSubscriptionMonthlyPriceFcfa)
                .filter(price -> price != null && price > 0)
                .orElse(5000.0);
    }

    private void seedSubscriptionPlans() {
        double monthlyPrice = defaultMonthlyPrice();

        if (subscriptionPlanRepository.findById("Free").isEmpty()) {
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setCode("Free");
            plan.setLabel("Gratuit");
            plan.setPrice(0.0);
            plan.setMaxUsers(10);
            plan.setMaxWarehouses(3);
            plan.setTrialDays(30);
            plan.setIsActive(true);
            subscriptionPlanRepository.save(plan);
            log.debug("Plan d'abonnement 'Free' créé");
        }

        subscriptionPlanRepository.findById("Free").ifPresent(plan -> {
            if (plan.getTrialDays() == null || plan.getTrialDays() != 30) {
                plan.setTrialDays(30);
                subscriptionPlanRepository.save(plan);
            }
        });

        if (subscriptionPlanRepository.findById("Basique").isEmpty()) {
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setCode("Basique");
            plan.setLabel("Basique");
            plan.setPrice(monthlyPrice);
            plan.setMaxUsers(5);
            plan.setMaxWarehouses(2);
            plan.setTrialDays(null);
            plan.setIsActive(false);
            subscriptionPlanRepository.save(plan);
            log.debug("Plan d'abonnement 'Basique' créé (inactif)");
        }
        
        if (subscriptionPlanRepository.findById("Standard").isEmpty()) {
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setCode("Standard");
            plan.setLabel("Standard");
            plan.setPrice(monthlyPrice);
            plan.setMaxUsers(20);
            plan.setMaxWarehouses(5);
            plan.setTrialDays(null);
            plan.setIsActive(true);
            subscriptionPlanRepository.save(plan);
            log.debug("Plan d'abonnement 'Standard' créé");
        }
        
        if (subscriptionPlanRepository.findById("Premium").isEmpty()) {
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setCode("Premium");
            plan.setLabel("Premium");
            plan.setPrice(monthlyPrice);
            plan.setMaxUsers(50);
            plan.setMaxWarehouses(10);
            plan.setTrialDays(null);
            plan.setIsActive(false);
            subscriptionPlanRepository.save(plan);
            log.debug("Plan d'abonnement 'Premium' créé (inactif)");
        }

        deactivateLegacyPaidPlans();
    }

    /** Un seul plan payant : Standard. Les anciens types (Basique, Premium) sont désactivés. */
    private void deactivateLegacyPaidPlans() {
        for (String legacyCode : List.of("Basique", "Premium")) {
            subscriptionPlanRepository.findById(legacyCode).ifPresent(plan -> {
                if (Boolean.TRUE.equals(plan.getIsActive())) {
                    plan.setIsActive(false);
                    subscriptionPlanRepository.save(plan);
                    log.debug("Plan '{}' désactivé (abonnement par durée uniquement)", legacyCode);
                }
            });
        }
    }
    
    private void seedSubscriptionDurations() {
        seedDuration("MONTH_1", "1 mois", 1, 0);
        seedDuration("MONTH_3", "3 mois", 3, 5);
        seedDuration("MONTH_6", "6 mois", 6, 10);
        seedDuration("YEAR_1", "1 an", 12, 15);
    }

    private void seedDuration(String code, String label, int months, double discountPercent) {
        subscriptionDurationRepository.findById(code).ifPresentOrElse(duration -> {
            if (duration.getDiscountPercent() == null || duration.getDiscountPercent() != discountPercent) {
                duration.setDiscountPercent(discountPercent);
                subscriptionDurationRepository.save(duration);
            }
        }, () -> {
            SubscriptionDuration duration = new SubscriptionDuration();
            duration.setCode(code);
            duration.setLabel(label);
            duration.setMonths(months);
            duration.setDiscountPercent(discountPercent);
            duration.setIsActive(true);
            subscriptionDurationRepository.save(duration);
            log.debug("Durée d'abonnement '{}' créée", code);
        });
    }

    private void backfillSubscriptionRecordsStatus() {
        subscriptionRecordRepository.findAll().forEach(record -> {
            if (record.getRequestStatus() == null) {
                record.setRequestStatus(
                        record.getPeriodStart() != null
                                ? SubscriptionRequestStatusCode.APPROVED
                                : SubscriptionRequestStatusCode.PENDING
                );
                subscriptionRecordRepository.save(record);
            }
        });
    }

    private void backfillCompanySubscriptions() {
        companyRepository.findAll().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .filter(c -> c.getSubscriptionStatus() == null || c.getTrialEndsAt() == null)
                .forEach(c -> {
                    java.time.LocalDateTime base = c.getCreatedAt() != null
                            ? c.getCreatedAt() : java.time.LocalDateTime.now();
                    c.setTrialEndsAt(base.plusMonths(SubscriptionService.TRIAL_MONTHS));
                    c.setSubscriptionStatus(SubscriptionStatusCode.TRIAL);
                    companyRepository.save(c);
                    log.debug("Essai 1 mois appliqué à l'entreprise: {}", c.getName());
                });
    }

    private void seedUserRoles() {
        if (userRoleRepository.findById("SUPER_ADMIN").isEmpty()) {
            UserRole role = new UserRole();
            role.setCode("SUPER_ADMIN");
            role.setLabel("Super Administrateur");
            role.setDescription("Accès complet à toute la plateforme, gestion des entreprises");
            role.setIsActive(true);
            userRoleRepository.save(role);
            log.debug("Rôle 'SUPER_ADMIN' créé");
        }
        
        if (userRoleRepository.findById("ADMIN_ENTREPRISE").isEmpty()) {
            UserRole role = new UserRole();
            role.setCode("ADMIN_ENTREPRISE");
            role.setLabel("Administrateur Entreprise");
            role.setDescription("Gestion complète de son entreprise (produits, entrepôts, utilisateurs)");
            role.setIsActive(true);
            userRoleRepository.save(role);
            log.debug("Rôle 'ADMIN_ENTREPRISE' créé");
        }
        
        if (userRoleRepository.findById("GESTIONNAIRE").isEmpty()) {
            UserRole role = new UserRole();
            role.setCode("GESTIONNAIRE");
            role.setLabel("Gestionnaire");
            role.setDescription("Gestion des mouvements de stock pour les entrepôts assignés");
            role.setIsActive(true);
            userRoleRepository.save(role);
            log.debug("Rôle 'GESTIONNAIRE' créé");
        }
        
        if (userRoleRepository.findById("UTILISATEUR").isEmpty()) {
            UserRole role = new UserRole();
            role.setCode("UTILISATEUR");
            role.setLabel("Utilisateur");
            role.setDescription("Consultation des données des entrepôts assignés");
            role.setIsActive(true);
            userRoleRepository.save(role);
            log.debug("Rôle 'UTILISATEUR' créé");
        }
    }
    
    private void seedCompanyStatuses() {
        if (companyStatusRepository.findById("Actif").isEmpty()) {
            CompanyStatus status = new CompanyStatus();
            status.setCode("Actif");
            status.setLabel("Actif");
            status.setIsActive(true);
            companyStatusRepository.save(status);
            log.debug("Statut entreprise 'Actif' créé");
        }
        
        if (companyStatusRepository.findById("Inactif").isEmpty()) {
            CompanyStatus status = new CompanyStatus();
            status.setCode("Inactif");
            status.setLabel("Inactif");
            status.setIsActive(true);
            companyStatusRepository.save(status);
            log.debug("Statut entreprise 'Inactif' créé");
        }
        
        if (companyStatusRepository.findById("Suspendu").isEmpty()) {
            CompanyStatus status = new CompanyStatus();
            status.setCode("Suspendu");
            status.setLabel("Suspendu");
            status.setIsActive(true);
            companyStatusRepository.save(status);
            log.debug("Statut entreprise 'Suspendu' créé");
        }
    }
    
    private void seedWarehouseStatuses() {
        if (warehouseStatusRepository.findById("Actif").isEmpty()) {
            WarehouseStatus status = new WarehouseStatus();
            status.setCode("Actif");
            status.setLabel("Actif");
            status.setIsActive(true);
            warehouseStatusRepository.save(status);
            log.debug("Statut entrepôt 'Actif' créé");
        }
        
        if (warehouseStatusRepository.findById("Inactif").isEmpty()) {
            WarehouseStatus status = new WarehouseStatus();
            status.setCode("Inactif");
            status.setLabel("Inactif");
            status.setIsActive(true);
            warehouseStatusRepository.save(status);
            log.debug("Statut entrepôt 'Inactif' créé");
        }
        
        if (warehouseStatusRepository.findById("Maintenance").isEmpty()) {
            WarehouseStatus status = new WarehouseStatus();
            status.setCode("Maintenance");
            status.setLabel("En maintenance");
            status.setIsActive(true);
            warehouseStatusRepository.save(status);
            log.debug("Statut entrepôt 'Maintenance' créé");
        }
    }
    
    private void seedProductStatuses() {
        if (productStatusRepository.findById("En stock").isEmpty()) {
            ProductStatus status = new ProductStatus();
            status.setCode("En stock");
            status.setLabel("En stock");
            status.setIsActive(true);
            productStatusRepository.save(status);
            log.debug("Statut produit 'En stock' créé");
        }
        
        if (productStatusRepository.findById("Rupture").isEmpty()) {
            ProductStatus status = new ProductStatus();
            status.setCode("Rupture");
            status.setLabel("Rupture de stock");
            status.setIsActive(true);
            productStatusRepository.save(status);
            log.debug("Statut produit 'Rupture' créé");
        }
    }
    
    private void seedProductCategories() {
        if (productCategoryRepository.findById("GENERAL").isEmpty()) {
            ProductCategory cat = new ProductCategory();
            cat.setCode("GENERAL");
            cat.setLabel("GENERAL");
            cat.setDescription("Catégorie générique par défaut");
            cat.setIsActive(true);
            productCategoryRepository.save(cat);
            log.debug("Catégorie 'GENERAL' créée");
        }
        if (productCategoryRepository.findById("ELECTRONIQUE").isEmpty()) {
            ProductCategory cat = new ProductCategory();
            cat.setCode("ELECTRONIQUE");
            cat.setLabel("Électronique");
            cat.setDescription("Produits électroniques et high-tech");
            cat.setIsActive(true);
            productCategoryRepository.save(cat);
            log.debug("Catégorie 'Électronique' créée");
        }
        if (productCategoryRepository.findById("ALIMENTAIRE").isEmpty()) {
            ProductCategory cat = new ProductCategory();
            cat.setCode("ALIMENTAIRE");
            cat.setLabel("Alimentaire");
            cat.setDescription("Produits alimentaires et boissons");
            cat.setIsActive(true);
            productCategoryRepository.save(cat);
            log.debug("Catégorie 'Alimentaire' créée");
        }
        if (productCategoryRepository.findById("TEXTILE").isEmpty()) {
            ProductCategory cat = new ProductCategory();
            cat.setCode("TEXTILE");
            cat.setLabel("Textile");
            cat.setDescription("Vêtements et textiles");
            cat.setIsActive(true);
            productCategoryRepository.save(cat);
            log.debug("Catégorie 'Textile' créée");
        }
        if (productCategoryRepository.findById("BEAUTE").isEmpty()) {
            ProductCategory cat = new ProductCategory();
            cat.setCode("BEAUTE");
            cat.setLabel("Beauté & Hygiène");
            cat.setDescription("Cosmétiques et produits d'hygiène");
            cat.setIsActive(true);
            productCategoryRepository.save(cat);
            log.debug("Catégorie 'Beauté & Hygiène' créée");
        }
        if (productCategoryRepository.findById("MAISON").isEmpty()) {
            ProductCategory cat = new ProductCategory();
            cat.setCode("MAISON");
            cat.setLabel("Maison & Décoration");
            cat.setDescription("Articles pour la maison et décoration");
            cat.setIsActive(true);
            productCategoryRepository.save(cat);
            log.debug("Catégorie 'Maison & Décoration' créée");
        }
        if (productCategoryRepository.findById("AUTRE").isEmpty()) {
            ProductCategory cat = new ProductCategory();
            cat.setCode("AUTRE");
            cat.setLabel("Autre");
            cat.setDescription("Autres catégories");
            cat.setIsActive(true);
            productCategoryRepository.save(cat);
            log.debug("Catégorie 'Autre' créée");
        }
    }
    
    private void seedMovementTypes() {
        if (movementTypeRepository.findById("ENTREE").isEmpty()) {
            MovementType type = new MovementType();
            type.setCode("ENTREE");
            type.setLabel("Entrée");
            type.setDescription("Ajout de produits en stock");
            type.setAllowsNegative(false);
            type.setRequiresDestination(false);
            type.setIsActive(true);
            movementTypeRepository.save(type);
            log.debug("Type de mouvement 'ENTREE' créé");
        }
        
        if (movementTypeRepository.findById("SORTIE").isEmpty()) {
            MovementType type = new MovementType();
            type.setCode("SORTIE");
            type.setLabel("Sortie");
            type.setDescription("Retrait de produits du stock");
            type.setAllowsNegative(false);
            type.setRequiresDestination(false);
            type.setIsActive(true);
            movementTypeRepository.save(type);
            log.debug("Type de mouvement 'SORTIE' créé");
        }
        
        if (movementTypeRepository.findById("TRANSFERT").isEmpty()) {
            MovementType type = new MovementType();
            type.setCode("TRANSFERT");
            type.setLabel("Transfert");
            type.setDescription("Déplacement de produits entre entrepôts");
            type.setAllowsNegative(false);
            type.setRequiresDestination(true);
            type.setIsActive(true);
            movementTypeRepository.save(type);
            log.debug("Type de mouvement 'TRANSFERT' créé");
        }
        
        if (movementTypeRepository.findById("AJUSTEMENT").isEmpty()) {
            MovementType type = new MovementType();
            type.setCode("AJUSTEMENT");
            type.setLabel("Ajustement");
            type.setDescription("Correction de stock (peut être positif ou négatif)");
            type.setAllowsNegative(true);
            type.setRequiresDestination(false);
            type.setIsActive(true);
            movementTypeRepository.save(type);
            log.debug("Type de mouvement 'AJUSTEMENT' créé");
        }
    }
    
    /**
     * Crée les utilisateurs de test pour chaque profil
     */
    private void seedTestUsers() {
        log.info("Création des utilisateurs de test...");
        
        // Créer le super admin
        seedSuperAdmin();
        
        // Créer une entreprise de test
        Company testCompany = seedTestCompany();
        
        // Créer des entrepôts de test
        Warehouse warehouse1 = seedTestWarehouse(testCompany, "Entrepôt Central", 1);
        Warehouse warehouse2 = seedTestWarehouse(testCompany, "Entrepôt Nord", 2);
        
        // Créer les utilisateurs pour chaque profil
        seedAdminEntreprise(testCompany);
        seedGestionnaire(testCompany, warehouse1, warehouse2);
        
        log.info("Utilisateurs de test créés avec succès !");
    }
    
    /**
     * Crée le super admin par défaut
     */
    private void seedSuperAdmin() {
        String superAdminEmail = "superadmin@yopmail.com";
        
        if (userRepository.findByEmail(superAdminEmail).isEmpty()) {
            UserRole superAdminRole = userRoleRepository.findById("SUPER_ADMIN")
                    .orElseThrow(() -> new RuntimeException("Rôle SUPER_ADMIN non trouvé"));
            
            User superAdmin = new User();
            superAdmin.setEmail(superAdminEmail);
            superAdmin.setPassword(passwordEncoder.encode("P@sser1234"));
            superAdmin.setName("Super Administrateur");
            superAdmin.setRole(superAdminRole);
            superAdmin.setStatus("Actif");
            superAdmin.setCompany(null); // Super admin n'a pas d'entreprise
            superAdmin.setIsDeleted(false);
            
            userRepository.save(superAdmin);
            
            log.info("✓ Super administrateur créé");
            log.info("  Email: {}", superAdminEmail);
            log.info("  Mot de passe: SuperAdmin123!");
        } else {
            log.info("Super administrateur existe déjà");
        }
    }
    
    /**
     * Crée une entreprise de test
     */
    private Company seedTestCompany() {
        String companyName = "Entreprise Test";
        
        Company company = companyRepository.findByName(companyName)
                .orElse(null);
        
        if (company == null) {
            SubscriptionPlan plan = subscriptionPlanRepository.findById("Free")
                    .orElseThrow(() -> new RuntimeException("Plan Free non trouvé"));
            
            CompanyStatus status = companyStatusRepository.findById("Actif")
                    .orElseThrow(() -> new RuntimeException("Statut Actif non trouvé"));
            
            company = new Company();
            company.setName(companyName);
            company.setEmail("contact@entreprisetest.com");
            company.setPhone("+221 77 123 45 67");
            company.setAddress("123 Rue Test, Dakar");
            company.setRegion("Dakar");
            company.setCountry("Sénégal");
            company.setPlan(plan);
            company.setStatus(status);
            company.setIsDeleted(false);
            subscriptionService.initializeTrialForNewCompany(company);

            company = companyRepository.save(company);
            log.info("✓ Entreprise de test créée: {}", companyName);
        } else {
            log.info("Entreprise de test existe déjà");
        }
        
        return company;
    }
    
    /**
     * Crée un entrepôt de test
     */
    private Warehouse seedTestWarehouse(Company company, String name, int index) {
        Warehouse warehouse = warehouseRepository.findAll().stream()
                .filter(w -> w.getCompany().getId().equals(company.getId()) && w.getName().equals(name))
                .findFirst()
                .orElse(null);
        
        if (warehouse == null) {
            WarehouseStatus status = warehouseStatusRepository.findById("Actif")
                    .orElseThrow(() -> new RuntimeException("Statut Actif non trouvé"));
            
            warehouse = new Warehouse();
            warehouse.setCompany(company);
            warehouse.setName(name);
            warehouse.setRegion(company.getRegion());
            warehouse.setDescription("Entrepôt " + name);
            warehouse.setStatus(status);
            warehouse.setIsDeleted(false);
            
            warehouse = warehouseRepository.save(warehouse);
            log.info("✓ Entrepôt créé: {}", name);
        }
        
        return warehouse;
    }
    
    /**
     * Crée un utilisateur Admin Entreprise
     */
    private void seedAdminEntreprise(Company company) {
        String email = "adminentreprise@yopmail.com";
        
        if (userRepository.findByEmail(email).isEmpty()) {
            UserRole role = userRoleRepository.findById("ADMIN_ENTREPRISE")
                    .orElseThrow(() -> new RuntimeException("Rôle ADMIN_ENTREPRISE non trouvé"));
            
            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode("P@sser1234"));
            user.setName("Admin Entreprise");
            user.setRole(role);
            user.setStatus("Actif");
            user.setCompany(company);
            user.setIsDeleted(false);
            
            userRepository.save(user);
            
            log.info("✓ Admin Entreprise créé");
            log.info("  Email: {}", email);
            log.info("  Mot de passe: Admin123!");
        } else {
            log.info("Admin Entreprise existe déjà");
        }
    }
    
    /**
     * Crée un utilisateur Gestionnaire
     */
    private void seedGestionnaire(Company company, Warehouse warehouse1, Warehouse warehouse2) {
        String email = "gestionnaire@entreprisetest.com";
        
        if (userRepository.findByEmail(email).isEmpty()) {
            UserRole role = userRoleRepository.findById("GESTIONNAIRE")
                    .orElseThrow(() -> new RuntimeException("Rôle GESTIONNAIRE non trouvé"));
            
            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode("Gestionnaire123!"));
            user.setName("Gestionnaire Test");
            user.setRole(role);
            user.setStatus("Actif");
            user.setCompany(company);
            user.setIsDeleted(false);
            
            user = userRepository.save(user);
            
            // Assigner les entrepôts au gestionnaire
            assignWarehouseToUser(user, warehouse1);
            assignWarehouseToUser(user, warehouse2);
            
            log.info("✓ Gestionnaire créé");
            log.info("  Email: {}", email);
            log.info("  Mot de passe: Gestionnaire123!");
            log.info("  Entrepôts assignés: {}, {}", warehouse1.getName(), warehouse2.getName());
        } else {
            log.info("Gestionnaire existe déjà");
        }
    }
    
    /**
     * Assigne un entrepôt à un utilisateur
     */
    private void assignWarehouseToUser(User user, Warehouse warehouse) {
        com.stocksaas.model.UserWarehouseId id = new com.stocksaas.model.UserWarehouseId();
        id.setUser(user.getId());
        id.setWarehouse(warehouse.getId());
        
        if (userWarehouseRepository.findById(id).isEmpty()) {
            com.stocksaas.model.UserWarehouse userWarehouse = new com.stocksaas.model.UserWarehouse();
            userWarehouse.setUser(user);
            userWarehouse.setWarehouse(warehouse);
            
            userWarehouseRepository.save(userWarehouse);
        }
    }
}
