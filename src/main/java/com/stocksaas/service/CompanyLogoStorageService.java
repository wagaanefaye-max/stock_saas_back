package com.stocksaas.service;

import com.stocksaas.dto.ProofResourceResult;
import com.stocksaas.exception.ProofNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;

@Service
@Slf4j
public class CompanyLogoStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    @Value("${app.upload.companies-logos-dir:uploads/companies/logos}")
    private String companiesLogosDir;

    @PostConstruct
    void ensureStorageDirectory() throws IOException {
        Path baseDir = Paths.get(companiesLogosDir).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);
        log.info("Stockage des logos entreprise : {}", baseDir);
    }

    public String storeLogo(Long companyId, MultipartFile file) throws IOException {
        validateImage(file);
        deleteLogoFiles(companyId);

        String contentType = normalizeContentType(file.getContentType());
        String extension = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };

        String filename = "company-" + companyId + "." + extension;
        Path target = baseDir().resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }

    public ProofResourceResult loadLogo(Long companyId) throws IOException {
        Path file = findLogoFile(companyId);
        if (file == null) {
            throw new ProofNotFoundException("Logo non disponible pour cette entreprise");
        }
        Resource resource = new UrlResource(file.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new ProofNotFoundException("Logo non disponible pour cette entreprise");
        }
        return new ProofResourceResult(resource, resolveContentType(file));
    }

    public void deleteLogo(Long companyId) throws IOException {
        deleteLogoFiles(companyId);
    }

    public boolean hasLogo(Long companyId) throws IOException {
        return findLogoFile(companyId) != null;
    }

    private Path findLogoFile(Long companyId) throws IOException {
        Path base = baseDir();
        if (!Files.exists(base)) {
            return null;
        }
        String prefix = "company-" + companyId + ".";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base, prefix + "*")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    return path;
                }
            }
        }
        return null;
    }

    private void deleteLogoFiles(Long companyId) throws IOException {
        Path file = findLogoFile(companyId);
        if (file != null) {
            Files.deleteIfExists(file);
        }
    }

    private Path baseDir() {
        return Paths.get(companiesLogosDir).toAbsolutePath().normalize();
    }

    private static void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("L'image du logo est obligatoire");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new RuntimeException("L'image ne doit pas dépasser 5 Mo");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new RuntimeException("Format non accepté. Utilisez une image JPEG, PNG, WebP ou GIF");
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        return contentType.toLowerCase().split(";")[0].trim();
    }

    private static String resolveContentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }
}
