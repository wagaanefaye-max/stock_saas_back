package com.stocksaas.service;

import com.stocksaas.dto.ProofResourceResult;
import com.stocksaas.dto.StoredProofFile;
import com.stocksaas.exception.ProofNotFoundException;
import com.stocksaas.model.Company;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    void ensureStorageDirectory() {
        try {
            Path baseDir = baseDir();
            Files.createDirectories(baseDir);
            log.info("Stockage des logos entreprise : {}", baseDir);
        } catch (IOException e) {
            log.warn(
                    "Dossier logos inaccessible ({}) : {}. L'application démarre ; les logos seront lus depuis la base si besoin.",
                    companiesLogosDir,
                    e.getMessage()
            );
        }
    }

    public StoredProofFile storeLogo(Long companyId, MultipartFile file) throws IOException {
        validateImage(file);

        String contentType = normalizeContentType(file.getContentType());
        String extension = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };

        byte[] data = file.getBytes();
        String filename = "company-" + companyId + "." + extension;
        writeToDisk(companyId, filename, data);
        return new StoredProofFile(filename, data, contentType);
    }

    public ProofResourceResult loadLogo(Company company) throws IOException {
        Resource resource = loadFromDisk(company.getId());
        if (resource == null) {
            resource = loadFromDatabase(company);
        }
        if (resource == null) {
            throw new ProofNotFoundException("Logo non disponible pour cette entreprise");
        }
        String contentType = resolveContentType(company, resource);
        return new ProofResourceResult(resource, contentType);
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

    private Resource loadFromDisk(Long companyId) throws IOException {
        Path file = findLogoFile(companyId);
        if (file == null) {
            return null;
        }
        Resource resource = new UrlResource(file.toUri());
        if (resource.exists() && resource.isReadable()) {
            return resource;
        }
        return null;
    }

    private Resource loadFromDatabase(Company company) {
        byte[] data = company.getLogoData();
        if (data == null || data.length == 0) {
            return null;
        }
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return "company-" + company.getId() + "-logo";
            }
        };
    }

    private String resolveContentType(Company company, Resource resource) throws IOException {
        if (company.getLogoContentType() != null && !company.getLogoContentType().isBlank()) {
            return company.getLogoContentType();
        }
        String filename = resource.getFilename();
        if (filename != null) {
            String name = filename.toLowerCase();
            if (name.endsWith(".png")) {
                return "image/png";
            }
            if (name.endsWith(".webp")) {
                return "image/webp";
            }
            if (name.endsWith(".gif")) {
                return "image/gif";
            }
        }
        return "image/jpeg";
    }

    private void writeToDisk(Long companyId, String filename, byte[] data) {
        try {
            deleteLogoFiles(companyId);
            Path target = baseDir();
            Files.createDirectories(target);
            Files.write(target.resolve(filename), data);
        } catch (IOException e) {
            log.warn("Impossible d'écrire le logo sur disque ({}): {}", filename, e.getMessage());
        }
    }
}
