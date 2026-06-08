package com.stocksaas.service;

import com.stocksaas.dto.PlatformSettingsDTO;
import com.stocksaas.model.PlatformSettings;
import com.stocksaas.model.User;
import com.stocksaas.repository.PlatformSettingsRepository;
import com.stocksaas.repository.SubscriptionPlanRepository;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.subscription.SubscriptionPricing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository platformSettingsRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PlatformSettingsDTO getSettings(String requesterEmail) {
        requireSuperAdmin(requesterEmail);
        return toDto(getOrCreate());
    }

    @Transactional(readOnly = true)
    public double getMonthlyPriceFcfa() {
        PlatformSettings settings = getOrCreate();
        Double price = settings.getSubscriptionMonthlyPriceFcfa();
        if (price == null || price <= 0) {
            return SubscriptionPricing.DEFAULT_MONTHLY_PRICE_FCFA;
        }
        return price;
    }

    @Transactional
    public PlatformSettingsDTO updateSettings(PlatformSettingsDTO dto, String requesterEmail) {
        requireSuperAdmin(requesterEmail);

        if (dto.getSubscriptionMonthlyPriceFcfa() == null || dto.getSubscriptionMonthlyPriceFcfa() <= 0) {
            throw new RuntimeException("Le montant mensuel doit être supérieur à 0");
        }

        PlatformSettings settings = getOrCreate();
        settings.setSubscriptionMonthlyPriceFcfa(dto.getSubscriptionMonthlyPriceFcfa());
        settings.setMaintenanceMode(Boolean.TRUE.equals(dto.getMaintenanceMode()));
        settings.setAllowNewRegistrations(dto.getAllowNewRegistrations() == null || dto.getAllowNewRegistrations());
        platformSettingsRepository.save(settings);

        syncPaidPlanPrices(settings.getSubscriptionMonthlyPriceFcfa());

        return toDto(settings);
    }

    private void syncPaidPlanPrices(double monthlyPrice) {
        subscriptionPlanRepository.findAll().stream()
                .filter(plan -> plan.getCode() != null && !"Free".equalsIgnoreCase(plan.getCode()))
                .forEach(plan -> {
                    plan.setPrice(monthlyPrice);
                    subscriptionPlanRepository.save(plan);
                });
    }

    private PlatformSettings getOrCreate() {
        return platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)
                .orElseGet(() -> {
                    PlatformSettings defaults = new PlatformSettings();
                    defaults.setId(PlatformSettings.SINGLETON_ID);
                    defaults.setSubscriptionMonthlyPriceFcfa(SubscriptionPricing.DEFAULT_MONTHLY_PRICE_FCFA);
                    defaults.setMaintenanceMode(false);
                    defaults.setAllowNewRegistrations(true);
                    return platformSettingsRepository.save(defaults);
                });
    }

    private void requireSuperAdmin(String email) {
        User user = userRepository.findByEmailWithCompanyAndRole(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (user.getRole() == null || !"SUPER_ADMIN".equals(user.getRole().getCode())) {
            throw new RuntimeException("Accès réservé au super administrateur");
        }
    }

    private PlatformSettingsDTO toDto(PlatformSettings settings) {
        return PlatformSettingsDTO.builder()
                .subscriptionMonthlyPriceFcfa(settings.getSubscriptionMonthlyPriceFcfa())
                .maintenanceMode(settings.getMaintenanceMode())
                .allowNewRegistrations(settings.getAllowNewRegistrations())
                .build();
    }
}
