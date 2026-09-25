package com.example.supportbot.service;

import com.example.supportbot.config.ObjectStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final HexFormat HEX = HexFormat.of();
    private static final Set<String> AVATAR_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private final Path attachmentsRoot;
    private final ObjectStorageProperties objectStorageProperties;
    private volatile S3Client s3Client;

    public AttachmentService(Path attachmentsRoot,
                             ObjectStorageProperties objectStorageProperties) {
        this.attachmentsRoot = attachmentsRoot;
        this.objectStorageProperties = objectStorageProperties;
    }

    public StoredAttachment store(String channelPublicId, String extension, InputStream dataStream) throws IOException {
        OffsetDateTime now = OffsetDateTime.now();
        String filename = buildFileName(extension);
        String storageKey = channelPublicId + "/" + DATE_PREFIX.format(now) + "/" + filename;
        if (!objectStorageProperties.isS3Mode()) {
            Path target = attachmentsRoot.resolve(storageKey).normalize();
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (InputStream in = dataStream) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Saved attachment {}", target);
            return new StoredAttachment(storageKey, "local_fs", target);
        }
        Path tempFile = Files.createTempFile("iguana-attachment-upload-", ".bin");
        try (InputStream in = dataStream) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            s3Client().putObject(
                    PutObjectRequest.builder()
                            .bucket(requiredBucket())
                            .key(objectKey("attachments", storageKey))
                            .build(),
                    RequestBody.fromFile(tempFile)
            );
        } finally {
            Files.deleteIfExists(tempFile);
        }
        log.info("Saved attachment to object storage key {}", storageKey);
        return new StoredAttachment(storageKey, "s3", null);
    }

    public Path materialize(String storageKey) throws IOException {
        if (!StringUtils.hasText(storageKey)) {
            throw new IllegalArgumentException("Attachment key is empty");
        }
        String normalized = storageKey.trim().replace('\\', '/');
        if (!objectStorageProperties.isS3Mode()) {
            Path resolved = attachmentsRoot.resolve(normalized).normalize();
            if (!resolved.startsWith(attachmentsRoot) || !Files.isRegularFile(resolved)) {
                throw new IllegalArgumentException("Attachment not found");
            }
            return resolved;
        }
        Path tempFile = Files.createTempFile("iguana-attachment-", "-" + Path.of(normalized).getFileName());
        try (ResponseInputStream<GetObjectResponse> response = s3Client().getObject(
                GetObjectRequest.builder()
                        .bucket(requiredBucket())
                        .key(objectKey("attachments", normalized))
                        .build()
        )) {
            Files.copy(response, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    public void cleanupMaterialized(Path path) throws IOException {
        if (path == null || !objectStorageProperties.isS3Mode()) {
            return;
        }
        Files.deleteIfExists(path);
    }

    public StoredAvatar storeClientAvatar(long userId,
                                          boolean full,
                                          String extension,
                                          String contentType,
                                          InputStream dataStream) throws IOException {
        if (userId <= 0 || dataStream == null) {
            throw new IllegalArgumentException("Invalid client avatar payload");
        }
        String normalizedExtension = normalizeAvatarExtension(extension);
        String storedName = userId + (full ? "_full" : "") + normalizedExtension;
        Path avatarsRoot = attachmentsRoot.resolve("avatars").normalize();
        Path localPath = null;

        if (!objectStorageProperties.isS3Mode()) {
            Path target = avatarsRoot.resolve(storedName).normalize();
            if (!target.startsWith(avatarsRoot)) {
                throw new IllegalArgumentException("Invalid client avatar storage key");
            }
            Files.createDirectories(avatarsRoot);
            try (InputStream in = dataStream) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            localPath = target;
        } else {
            Path tempFile = Files.createTempFile("iguana-client-avatar-", normalizedExtension);
            try (InputStream in = dataStream) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                PutObjectRequest.Builder request = PutObjectRequest.builder()
                        .bucket(requiredBucket())
                        .key(objectKey("avatars", storedName));
                if (StringUtils.hasText(contentType)) {
                    request.contentType(contentType.trim());
                }
                s3Client().putObject(request.build(), RequestBody.fromFile(tempFile));
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        removeObsoleteClientAvatarVariants(userId, full, storedName, avatarsRoot);
        log.info("Saved client avatar {} using {} storage", storedName, objectStorageProperties.isS3Mode() ? "s3" : "local_fs");
        return new StoredAvatar(storedName, objectStorageProperties.isS3Mode() ? "s3" : "local_fs", localPath);
    }

    private String normalizeAvatarExtension(String extension) {
        String normalized = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(".")) {
            normalized = "." + normalized;
        }
        if (!AVATAR_EXTENSIONS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported client avatar extension: " + extension);
        }
        return normalized;
    }

    private void removeObsoleteClientAvatarVariants(long userId, boolean full, String keepStoredName, Path avatarsRoot) {
        String prefix = userId + (full ? "_full" : "");
        for (String extension : AVATAR_EXTENSIONS) {
            String candidate = prefix + extension;
            if (candidate.equals(keepStoredName)) {
                continue;
            }
            try {
                if (!objectStorageProperties.isS3Mode()) {
                    Path target = avatarsRoot.resolve(candidate).normalize();
                    if (target.startsWith(avatarsRoot)) {
                        Files.deleteIfExists(target);
                    }
                } else {
                    s3Client().deleteObject(DeleteObjectRequest.builder()
                            .bucket(requiredBucket())
                            .key(objectKey("avatars", candidate))
                            .build());
                }
            } catch (Exception ex) {
                log.debug("Unable to remove obsolete client avatar variant {}: {}", candidate, ex.getMessage());
            }
        }
    }

    private String buildFileName(String extension) {
        byte[] randomBytes = new byte[12];
        ThreadLocalRandom.current().nextBytes(randomBytes);
        String base = HEX.formatHex(randomBytes);
        if (extension != null && !extension.isBlank()) {
            if (!extension.startsWith(".")) {
                return base + "." + extension;
            }
            return base + extension;
        }
        return base;
    }

    private String requiredBucket() {
        if (!StringUtils.hasText(objectStorageProperties.getBucket())) {
            throw new IllegalStateException("S3 bucket is not configured for bot attachment storage");
        }
        return objectStorageProperties.getBucket().trim();
    }

    private String objectKey(String domain, String storageKey) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(objectStorageProperties.getKeyPrefix())) {
            builder.append(objectStorageProperties.getKeyPrefix().trim().replace('\\', '/').replaceAll("/+$", ""));
            builder.append('/');
        }
        builder.append(domain);
        builder.append('/');
        builder.append(storageKey.trim().replace('\\', '/').replaceAll("^/+", ""));
        return builder.toString();
    }

    private S3Client s3Client() {
        S3Client current = s3Client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (s3Client != null) {
                return s3Client;
            }
            var builder = S3Client.builder()
                    .region(Region.of(StringUtils.hasText(objectStorageProperties.getRegion())
                            ? objectStorageProperties.getRegion().trim()
                            : "us-east-1"))
                    .forcePathStyle(objectStorageProperties.isPathStyleAccess());
            if (StringUtils.hasText(objectStorageProperties.getEndpoint())) {
                builder.endpointOverride(URI.create(objectStorageProperties.getEndpoint().trim()));
            }
            if (StringUtils.hasText(objectStorageProperties.getAccessKey())
                    || StringUtils.hasText(objectStorageProperties.getSecretKey())) {
                builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        StringUtils.hasText(objectStorageProperties.getAccessKey()) ? objectStorageProperties.getAccessKey().trim() : "",
                        StringUtils.hasText(objectStorageProperties.getSecretKey()) ? objectStorageProperties.getSecretKey().trim() : ""
                )));
            }
            s3Client = builder.build();
            return s3Client;
        }
    }

    public record StoredAttachment(String storageKey,
                                   String storageProvider,
                                   Path localPath) {
    }

    public record StoredAvatar(String storedName,
                               String storageProvider,
                               Path localPath) {
    }
}
