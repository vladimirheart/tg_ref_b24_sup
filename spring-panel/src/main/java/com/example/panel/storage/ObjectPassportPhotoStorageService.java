package com.example.panel.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

@Service
public class ObjectPassportPhotoStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectPassportPhotoStorageService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"
    );
    private static final Map<String, String> MIME_EXTENSION_MAP = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/pjpeg", ".jpg",
            "image/gif", ".gif",
            "image/webp", ".webp",
            "image/bmp", ".bmp",
            "image/x-png", ".png",
            "image/x-ms-bmp", ".bmp"
    );

    private final Path passportPhotosRoot;

    public ObjectPassportPhotoStorageService(
            @Value("${app.storage.passport-photos:attachments/passport_photos}") String passportPhotosDir
    ) throws IOException {
        this.passportPhotosRoot = ensureDirectory(passportPhotosDir);
    }

    public StoredPhoto store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }
        String extension = resolveExtension(file.getOriginalFilename(), file.getContentType());
        if (!StringUtils.hasText(extension) || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Поддерживаются изображения PNG, JPG, GIF, BMP или WebP.");
        }
        String originalName = StringUtils.cleanPath(
                StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "photo" + extension
        );
        String storedName = System.currentTimeMillis() + "_" + UUID.randomUUID() + extension;
        Path target = passportPhotosRoot.resolve(storedName).normalize();
        if (!target.startsWith(passportPhotosRoot)) {
            throw new IllegalArgumentException("Некорректный путь для сохранения файла");
        }
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        String mimeType = probeContentType(target, file.getContentType());
        OffsetDateTime uploadedAt = OffsetDateTime.now();
        return new StoredPhoto(
                originalName,
                storedName,
                buildPhotoUrl(storedName),
                mimeType,
                Files.size(target),
                uploadedAt.toString()
        );
    }

    public StoredPhoto store(InputStream inputStream,
                             String originalFilename,
                             String contentType) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Р¤Р°Р№Р» РЅРµ РјРѕР¶РµС‚ Р±С‹С‚СЊ РїСѓСЃС‚С‹Рј");
        }
        PushbackInputStream sniffableInputStream = new PushbackInputStream(inputStream, 32);
        inputStream = sniffableInputStream;
        String extension = resolveExtension(originalFilename, contentType);
        if (!StringUtils.hasText(extension) || !ALLOWED_EXTENSIONS.contains(extension)) {
            extension = sniffImageExtension(sniffableInputStream, extension);
        }
        if (!StringUtils.hasText(extension) || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("РџРѕРґРґРµСЂР¶РёРІР°СЋС‚СЃСЏ РёР·РѕР±СЂР°Р¶РµРЅРёСЏ PNG, JPG, GIF, BMP РёР»Рё WebP.");
        }
        String originalName = StringUtils.cleanPath(
                StringUtils.hasText(originalFilename) ? originalFilename : "photo" + extension
        );
        String storedName = System.currentTimeMillis() + "_" + UUID.randomUUID() + extension;
        Path target = passportPhotosRoot.resolve(storedName).normalize();
        if (!target.startsWith(passportPhotosRoot)) {
            throw new IllegalArgumentException("РќРµРєРѕСЂСЂРµРєС‚РЅС‹Р№ РїСѓС‚СЊ РґР»СЏ СЃРѕС…СЂР°РЅРµРЅРёСЏ С„Р°Р№Р»Р°");
        }
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        String mimeType = probeContentType(target, contentType);
        OffsetDateTime uploadedAt = OffsetDateTime.now();
        return new StoredPhoto(
                originalName,
                storedName,
                buildPhotoUrl(storedName),
                mimeType,
                Files.size(target),
                uploadedAt.toString()
        );
    }

    public ResponseEntity<Resource> download(String storedName) throws IOException {
        Path resolved = resolveStoredPhoto(storedName);
        String filename = resolved.getFileName() != null ? resolved.getFileName().toString() : "photo";
        String mimeType = probeContentType(resolved, null);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("inline")
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(Files.size(resolved))
                .body(new InputStreamResource(Files.newInputStream(resolved)));
    }

    public void delete(String storedName) throws IOException {
        Path resolved = resolveStoredPhotoForDeletion(storedName);
        Files.deleteIfExists(resolved);
    }

    public void deleteQuietly(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            return;
        }
        try {
            delete(storedName);
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Failed to delete passport photo {}", storedName, ex);
        }
    }

    public String buildPhotoUrl(String storedName) {
        String cleaned = StringUtils.cleanPath(storedName == null ? "" : storedName).trim();
        if (!StringUtils.hasText(cleaned)) {
            return "";
        }
        return "/api/object_passports/photos/file/" + cleaned;
    }

    private Path resolveStoredPhoto(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            throw new IllegalArgumentException("Файл не найден");
        }
        Path resolved = passportPhotosRoot.resolve(StringUtils.cleanPath(storedName)).normalize();
        if (!resolved.startsWith(passportPhotosRoot) || !Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("Файл не найден");
        }
        return resolved;
    }

    private Path resolveStoredPhotoForDeletion(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            throw new IllegalArgumentException("Файл не найден");
        }
        Path resolved = passportPhotosRoot.resolve(StringUtils.cleanPath(storedName)).normalize();
        if (!resolved.startsWith(passportPhotosRoot)) {
            throw new IllegalArgumentException("Файл не найден");
        }
        return resolved;
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String original = StringUtils.hasText(originalFilename) ? originalFilename.trim().toLowerCase() : "";
        int extensionIndex = original.lastIndexOf('.');
        if (extensionIndex >= 0 && extensionIndex < original.length() - 1) {
            return normalizeExtension(original.substring(extensionIndex));
        }
        if (StringUtils.hasText(contentType)) {
            return MIME_EXTENSION_MAP.getOrDefault(normalizeContentType(contentType), "");
        }
        return "";
    }

    private String normalizeExtension(String extension) {
        if (!StringUtils.hasText(extension)) {
            return "";
        }
        String normalized = extension.trim().toLowerCase();
        if (".jfif".equals(normalized)) {
            return ".jpg";
        }
        return normalized;
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        String normalized = contentType.trim().toLowerCase();
        int delimiterIndex = normalized.indexOf(';');
        return delimiterIndex >= 0 ? normalized.substring(0, delimiterIndex).trim() : normalized;
    }

    private String sniffImageExtension(PushbackInputStream inputStream, String currentExtension) throws IOException {
        byte[] header = inputStream.readNBytes(16);
        if (header.length > 0) {
            inputStream.unread(header);
        }
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && (header[4] & 0xFF) == 0x0D
                && (header[5] & 0xFF) == 0x0A
                && (header[6] & 0xFF) == 0x1A
                && (header[7] & 0xFF) == 0x0A) {
            return ".png";
        }
        if (header.length >= 6) {
            String gifHeader = new String(header, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(gifHeader) || "GIF89a".equals(gifHeader)) {
                return ".gif";
            }
        }
        if (header.length >= 2 && header[0] == 0x42 && header[1] == 0x4D) {
            return ".bmp";
        }
        if (header.length >= 12) {
            String riffHeader = new String(header, 0, 4, StandardCharsets.US_ASCII);
            String webpHeader = new String(header, 8, 4, StandardCharsets.US_ASCII);
            if ("RIFF".equals(riffHeader) && "WEBP".equals(webpHeader)) {
                return ".webp";
            }
        }
        return normalizeExtension(currentExtension);
    }

    private String probeContentType(Path target, String fallbackMimeType) throws IOException {
        String detected = Files.probeContentType(target);
        if (StringUtils.hasText(detected)) {
            return detected;
        }
        if (StringUtils.hasText(fallbackMimeType)) {
            return fallbackMimeType;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private Path ensureDirectory(String directory) throws IOException {
        Path path = Paths.get(directory).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path;
    }

    public record StoredPhoto(String originalName,
                              String storedName,
                              String url,
                              String mimeType,
                              long size,
                              String uploadedAt) {
    }
}
