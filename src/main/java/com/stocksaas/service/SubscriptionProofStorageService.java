package com.stocksaas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class SubscriptionProofStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    @Value("${app.upload.subscriptions-dir:uploads/subscriptions}")
    private String subscriptionsDir;

    public String storeProof(MultipartFile file, Long companyId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("La capture de paiement est obligatoire (Wave ou Orange Money)");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new RuntimeException("La capture ne doit pas dépasser 5 Mo");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException("Format non accepté. Utilisez une image JPEG, PNG ou WebP");
        }

        Path baseDir = Paths.get(subscriptionsDir).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);

        String extension = switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String filename = "company-" + companyId + "-" + UUID.randomUUID() + "." + extension;
        Path target = baseDir.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }

    public Resource loadProof(String proofFilePath) throws IOException {
        if (proofFilePath == null || proofFilePath.isBlank()) {
            throw new RuntimeException("Justificatif introuvable");
        }
        Path file = Paths.get(subscriptionsDir).toAbsolutePath().normalize().resolve(proofFilePath);
        Resource resource = new UrlResource(file.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("Fichier justificatif introuvable");
        }
        return resource;
    }
}
