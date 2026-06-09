package com.stocksaas.service;

import com.stocksaas.dto.ProofResourceResult;
import com.stocksaas.dto.StoredProofFile;
import com.stocksaas.exception.ProofNotFoundException;
import com.stocksaas.model.CompanySubscriptionRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
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

    public StoredProofFile storeProof(MultipartFile file, Long companyId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("La capture de paiement est obligatoire (Wave ou Orange Money)");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new RuntimeException("La capture ne doit pas dépasser 5 Mo");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new RuntimeException("Format non accepté. Utilisez une image JPEG, PNG ou WebP");
        }

        byte[] data = file.getBytes();
        Path baseDir = Paths.get(subscriptionsDir).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);

        String extension = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String filename = "company-" + companyId + "-" + UUID.randomUUID() + "." + extension;
        Path target = baseDir.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredProofFile(filename, data, contentType);
    }

    public ProofResourceResult loadProof(CompanySubscriptionRecord record) throws IOException {
        Resource resource = loadFromDisk(record.getProofFilePath());
        if (resource == null) {
            resource = loadFromDatabase(record);
        }
        if (resource == null) {
            throw new ProofNotFoundException(
                    "Le justificatif n'est plus disponible. L'entreprise peut soumettre une nouvelle capture de paiement.");
        }
        String contentType = resolveContentType(record, resource);
        return new ProofResourceResult(resource, contentType);
    }

    private Resource loadFromDisk(String proofFilePath) throws IOException {
        if (proofFilePath == null || proofFilePath.isBlank()) {
            return null;
        }
        Path file = Paths.get(subscriptionsDir).toAbsolutePath().normalize().resolve(proofFilePath);
        Resource resource = new UrlResource(file.toUri());
        if (resource.exists() && resource.isReadable()) {
            return resource;
        }
        return null;
    }

    private Resource loadFromDatabase(CompanySubscriptionRecord record) {
        byte[] data = record.getProofFileData();
        if (data == null || data.length == 0) {
            return null;
        }
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return record.getProofFilePath() != null ? record.getProofFilePath() : "justificatif.jpg";
            }
        };
    }

    private String resolveContentType(CompanySubscriptionRecord record, Resource resource) {
        if (record.getProofContentType() != null && !record.getProofContentType().isBlank()) {
            return record.getProofContentType();
        }
        String filename = resource.getFilename();
        if (filename != null) {
            if (filename.endsWith(".png")) {
                return "image/png";
            }
            if (filename.endsWith(".webp")) {
                return "image/webp";
            }
        }
        return "image/jpeg";
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.toLowerCase();
    }
}
