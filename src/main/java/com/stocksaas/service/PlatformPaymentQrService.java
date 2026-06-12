package com.stocksaas.service;

import com.stocksaas.dto.PaymentQrAvailabilityDTO;
import com.stocksaas.dto.ProofResourceResult;
import com.stocksaas.exception.ProofNotFoundException;
import com.stocksaas.model.PlatformSettings;
import com.stocksaas.repository.PlatformSettingsRepository;
import com.stocksaas.subscription.PaymentProviderCode;
import com.stocksaas.subscription.SubscriptionPricing;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlatformPaymentQrService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    private final PlatformSettingsRepository platformSettingsRepository;

    @Transactional(readOnly = true)
    public PaymentQrAvailabilityDTO getAvailability() {
        PlatformSettings settings = getOrCreate();
        return PaymentQrAvailabilityDTO.builder()
                .wave(hasQr(settings.getWaveQrData()))
                .orangeMoney(hasQr(settings.getOrangeMoneyQrData()))
                .build();
    }

    @Transactional(readOnly = true)
    public ProofResourceResult loadPaymentQr(String providerCode) throws IOException {
        PlatformSettings settings = getOrCreate();
        String normalized = normalizeProvider(providerCode);

        byte[] data;
        String contentType;
        if (PaymentProviderCode.WAVE.equals(normalized)) {
            data = settings.getWaveQrData();
            contentType = settings.getWaveQrContentType();
        } else {
            data = settings.getOrangeMoneyQrData();
            contentType = settings.getOrangeMoneyQrContentType();
        }

        if (!hasQr(data)) {
            throw new ProofNotFoundException("QR code " + PaymentProviderCode.label(normalized) + " non configuré");
        }

        String resolvedType = contentType != null && !contentType.isBlank()
                ? contentType
                : "image/png";
        return new ProofResourceResult(new ByteArrayResource(data), resolvedType);
    }

    @Transactional
    public void uploadPaymentQr(String providerCode, MultipartFile file) throws IOException {
        validateImage(file);
        PlatformSettings settings = getOrCreate();
        String normalized = normalizeProvider(providerCode);
        byte[] data = file.getBytes();
        String contentType = normalizeContentType(file.getContentType());

        if (PaymentProviderCode.WAVE.equals(normalized)) {
            settings.setWaveQrData(data);
            settings.setWaveQrContentType(contentType);
        } else {
            settings.setOrangeMoneyQrData(data);
            settings.setOrangeMoneyQrContentType(contentType);
        }
        platformSettingsRepository.save(settings);
    }

    @Transactional
    public void deletePaymentQr(String providerCode) {
        PlatformSettings settings = getOrCreate();
        String normalized = normalizeProvider(providerCode);

        if (PaymentProviderCode.WAVE.equals(normalized)) {
            settings.setWaveQrData(null);
            settings.setWaveQrContentType(null);
        } else {
            settings.setOrangeMoneyQrData(null);
            settings.setOrangeMoneyQrContentType(null);
        }
        platformSettingsRepository.save(settings);
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

    private static boolean hasQr(byte[] data) {
        return data != null && data.length > 0;
    }

    private static String normalizeProvider(String providerCode) {
        if (providerCode == null) {
            throw new RuntimeException("Fournisseur de paiement invalide");
        }
        String normalized = providerCode.trim().toUpperCase();
        if (!PaymentProviderCode.WAVE.equals(normalized) && !PaymentProviderCode.ORANGE_MONEY.equals(normalized)) {
            throw new RuntimeException("QR code disponible uniquement pour Wave et Orange Money");
        }
        return normalized;
    }

    private static void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("L'image du QR code est obligatoire");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new RuntimeException("L'image ne doit pas dépasser 5 Mo");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new RuntimeException("Format non accepté. Utilisez une image JPEG, PNG ou WebP");
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        return contentType.toLowerCase().split(";")[0].trim();
    }
}
