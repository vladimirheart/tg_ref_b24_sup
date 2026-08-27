package com.example.panel.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class AttachmentObjectStorageService {

    private final ObjectStorageProperties properties;
    private final Path attachmentsRoot;
    private final Path knowledgeBaseRoot;
    private final Path passportPhotosRoot;
    private final Path avatarsRoot;
    private volatile S3Client s3Client;

    public AttachmentObjectStorageService(ObjectStorageProperties properties,
                                          @Value("${app.storage.attachments:attachments}") String attachmentsDir,
                                          @Value("${app.storage.knowledge-base:attachments/knowledge_base}") String knowledgeBaseDir,
                                          @Value("${app.storage.passport-photos:attachments/passport_photos}") String passportPhotosDir,
                                          @Value("${app.storage.avatars:attachments/avatars}") String avatarsDir) throws IOException {
        this.properties = properties;
        this.attachmentsRoot = ensureDirectory(attachmentsDir);
        this.knowledgeBaseRoot = ensureDirectory(knowledgeBaseDir);
        this.passportPhotosRoot = ensureDirectory(passportPhotosDir);
        this.avatarsRoot = ensureDirectory(avatarsDir);
    }

    public void verifyReadyForPostgresql() {
        if (!properties.isRequiredForPostgresql()) {
            return;
        }
        verifyAvailable();
    }

    public void verifyAvailable() {
        if (!properties.isS3Mode()) {
            throw new IllegalStateException("S3 object storage requires app.storage.object.mode=s3.");
        }
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new IllegalStateException("S3 object storage bucket is not configured.");
        }
        s3Client().headBucket(HeadBucketRequest.builder().bucket(properties.getBucket().trim()).build());
    }

    public boolean isLegacyLocalFallbackEnabled() {
        return properties.isLegacyLocalFallbackEnabled();
    }

    public String providerLabel() {
        return properties.isS3Mode() ? "s3" : "local_fs";
    }

    public StoredBinary storeDialogAttachment(String ticketId,
                                              String storedName,
                                              String contentType,
                                              InputStream inputStream) throws IOException {
        return storeBinary(attachmentsRoot, buildDialogStorageKey(ticketId, storedName), "attachments", contentType, inputStream);
    }

    public StoredBinary openDialogAttachment(String ticketId, String storedName) throws IOException {
        return openBinary(attachmentsRoot, buildDialogStorageKey(ticketId, storedName), "attachments");
    }

    public StoredBinary openDialogAttachmentByStorageKey(String storageKey) throws IOException {
        return openBinary(attachmentsRoot, normalizeStorageKey(storageKey), "attachments");
    }

    public boolean dialogAttachmentExistsByStorageKey(String storageKey) {
        String normalized = normalizeStorageKey(storageKey);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        try {
            if (!properties.isS3Mode()) {
                Path resolved = attachmentsRoot.resolve(normalized).normalize();
                return resolved.startsWith(attachmentsRoot) && Files.isRegularFile(resolved);
            }
            s3Client().headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket().trim())
                    .key(objectKey("attachments", normalized))
                    .build());
            return true;
        } catch (RuntimeException ex) {
            if (!isLegacyLocalFallbackEnabled()) {
                return false;
            }
            return ensureLegacyLocalBinaryAvailable(attachmentsRoot, normalized, "attachments");
        }
    }

    public boolean backfillDialogAttachmentByStorageKey(String storageKey) {
        String normalized = normalizeStorageKey(storageKey);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        Path localFallback = resolveExistingLocalBinary(attachmentsRoot, normalized);
        if (localFallback == null) {
            return false;
        }
        return backfillLegacyLocalBinary(localFallback, normalized, "attachments") != BackfillOutcome.FAILED;
    }

    public void deleteDialogAttachment(String ticketId, String storedName) throws IOException {
        deleteBinary(attachmentsRoot, buildDialogStorageKey(ticketId, storedName), "attachments");
    }

    public StoredBinary storeKnowledgeBaseFile(String storedName,
                                               String contentType,
                                               InputStream inputStream) throws IOException {
        return storeBinary(knowledgeBaseRoot, normalizeStorageKey(storedName), "knowledge_base", contentType, inputStream);
    }

    public StoredBinary openKnowledgeBaseFile(String storedName) throws IOException {
        return openBinary(knowledgeBaseRoot, normalizeStorageKey(storedName), "knowledge_base");
    }

    public void deleteKnowledgeBaseFile(String storedName) throws IOException {
        deleteBinary(knowledgeBaseRoot, normalizeStorageKey(storedName), "knowledge_base");
    }

    public StoredBinary storePassportPhoto(String storedName,
                                           String contentType,
                                           InputStream inputStream) throws IOException {
        return storeBinary(passportPhotosRoot, normalizeStorageKey(storedName), "passport_photos", inputStream == null ? null : contentType, inputStream);
    }

    public StoredBinary openPassportPhoto(String storedName) throws IOException {
        return openBinary(passportPhotosRoot, normalizeStorageKey(storedName), "passport_photos");
    }

    public void deletePassportPhoto(String storedName) throws IOException {
        deleteBinary(passportPhotosRoot, normalizeStorageKey(storedName), "passport_photos");
    }

    public StoredBinary storeAvatar(String storedName,
                                    String contentType,
                                    InputStream inputStream) throws IOException {
        return storeBinary(avatarsRoot, normalizeStorageKey(storedName), "avatars", contentType, inputStream);
    }

    public StoredBinary openAvatar(String storedName) throws IOException {
        return openBinary(avatarsRoot, normalizeStorageKey(storedName), "avatars");
    }

    public boolean avatarExists(String storedName) {
        String normalized = normalizeStorageKey(storedName);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        try {
            if (!properties.isS3Mode()) {
                Path resolved = avatarsRoot.resolve(normalized).normalize();
                return resolved.startsWith(avatarsRoot) && Files.isRegularFile(resolved);
            }
            s3Client().headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket().trim())
                    .key(objectKey("avatars", normalized))
                    .build());
            return true;
        } catch (RuntimeException ex) {
            if (!isLegacyLocalFallbackEnabled()) {
                return false;
            }
            return ensureLegacyLocalBinaryAvailable(avatarsRoot, normalized, "avatars");
        }
    }

    public void deleteAvatar(String storedName) throws IOException {
        deleteBinary(avatarsRoot, normalizeStorageKey(storedName), "avatars");
    }

    private StoredBinary storeBinary(Path localRoot,
                                     String logicalKey,
                                     String domain,
                                     String contentType,
                                     InputStream inputStream) throws IOException {
        if (!StringUtils.hasText(logicalKey) || inputStream == null) {
            throw new IllegalArgumentException("Invalid storage payload");
        }
        String normalized = logicalKey.trim().replace('\\', '/');
        if (!properties.isS3Mode()) {
            Path target = localRoot.resolve(normalized).normalize();
            if (!target.startsWith(localRoot)) {
                throw new IllegalArgumentException("Invalid storage key");
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (InputStream in = inputStream) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredBinary(
                    normalized,
                    probeContentType(target, contentType),
                    Files.size(target),
                    Files.newInputStream(target)
            );
        }
        byte[] payload;
        try (InputStream in = inputStream) {
            payload = in.readAllBytes();
        }
        s3Client().putObject(
                PutObjectRequest.builder()
                        .bucket(properties.getBucket().trim())
                        .key(objectKey(domain, normalized))
                        .contentType(StringUtils.hasText(contentType) ? contentType.trim() : null)
                        .build(),
                RequestBody.fromBytes(payload)
        );
        return new StoredBinary(
                normalized,
                StringUtils.hasText(contentType) ? contentType.trim() : "application/octet-stream",
                payload.length,
                new java.io.ByteArrayInputStream(payload)
        );
    }

    private StoredBinary openBinary(Path localRoot, String logicalKey, String domain) throws IOException {
        if (!StringUtils.hasText(logicalKey)) {
            throw new IllegalArgumentException("File not found");
        }
        if (!properties.isS3Mode()) {
            return openLocalBinary(localRoot, logicalKey);
        }
        try {
            ResponseInputStream<GetObjectResponse> response = s3Client().getObject(
                    GetObjectRequest.builder()
                            .bucket(properties.getBucket().trim())
                            .key(objectKey(domain, logicalKey))
                            .build()
            );
            return new StoredBinary(
                    logicalKey,
                    StringUtils.hasText(response.response().contentType())
                            ? response.response().contentType()
                            : "application/octet-stream",
                    response.response().contentLength(),
                    response
            );
        } catch (RuntimeException ex) {
            if (!isLegacyLocalFallbackEnabled()) {
                throw ex;
            }
            Path localFallback = resolveExistingLocalBinary(localRoot, logicalKey);
            if (localFallback == null) {
                throw ex;
            }
            backfillLegacyLocalBinary(localFallback, logicalKey, domain);
            return new StoredBinary(
                    logicalKey,
                    probeContentType(localFallback, null),
                    Files.size(localFallback),
                    Files.newInputStream(localFallback)
            );
        }
    }

    private void deleteBinary(Path localRoot, String logicalKey, String domain) throws IOException {
        if (!StringUtils.hasText(logicalKey)) {
            return;
        }
        if (!properties.isS3Mode()) {
            Path resolved = localRoot.resolve(logicalKey).normalize();
            if (!resolved.startsWith(localRoot)) {
                throw new IllegalArgumentException("Invalid storage key");
            }
            Files.deleteIfExists(resolved);
            return;
        }
        s3Client().deleteObject(builder -> builder
                .bucket(properties.getBucket().trim())
                .key(objectKey(domain, logicalKey))
                .build());
    }

    private String buildDialogStorageKey(String ticketId, String storedName) {
        String safeTicketId = normalizeStorageKey(ticketId);
        String safeStoredName = normalizeStorageKey(storedName);
        if (!StringUtils.hasText(safeTicketId) || !StringUtils.hasText(safeStoredName)) {
            throw new IllegalArgumentException("Invalid dialog attachment key");
        }
        return safeTicketId + "/" + safeStoredName;
    }

    private String objectKey(String domain, String logicalKey) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(properties.getKeyPrefix())) {
            builder.append(properties.getKeyPrefix().trim().replace('\\', '/').replaceAll("/+$", ""));
            builder.append('/');
        }
        builder.append(domain);
        builder.append('/');
        builder.append(logicalKey.trim().replace('\\', '/').replaceAll("^/+", ""));
        return builder.toString();
    }

    private String normalizeStorageKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private StoredBinary openLocalBinary(Path localRoot, String logicalKey) throws IOException {
        Path resolved = resolveExistingLocalBinary(localRoot, logicalKey);
        if (resolved == null) {
            throw new IllegalArgumentException("File not found");
        }
        return new StoredBinary(
                logicalKey,
                probeContentType(resolved, null),
                Files.size(resolved),
                Files.newInputStream(resolved)
        );
    }

    private boolean ensureLegacyLocalBinaryAvailable(Path localRoot, String logicalKey, String domain) {
        Path resolved = resolveExistingLocalBinary(localRoot, logicalKey);
        if (resolved == null) {
            return false;
        }
        backfillLegacyLocalBinary(resolved, logicalKey, domain);
        return true;
    }

    private Path resolveExistingLocalBinary(Path localRoot, String logicalKey) {
        if (!StringUtils.hasText(logicalKey)) {
            return null;
        }
        Path resolved = localRoot.resolve(logicalKey).normalize();
        if (!resolved.startsWith(localRoot) || !Files.isRegularFile(resolved)) {
            return null;
        }
        return resolved;
    }

    private BackfillOutcome backfillLegacyLocalBinary(Path localFile, String logicalKey, String domain) {
        if (!properties.isS3Mode() || localFile == null || !Files.isRegularFile(localFile)) {
            return BackfillOutcome.ALREADY_PRESENT;
        }
        try {
            if (objectExists(domain, logicalKey)) {
                return BackfillOutcome.ALREADY_PRESENT;
            }
        } catch (RuntimeException ex) {
            return BackfillOutcome.FAILED;
        }
        try (InputStream inputStream = Files.newInputStream(localFile)) {
            storeBinary(
                    switch (domain) {
                        case "knowledge_base" -> knowledgeBaseRoot;
                        case "passport_photos" -> passportPhotosRoot;
                        case "avatars" -> avatarsRoot;
                        default -> attachmentsRoot;
                    },
                    logicalKey,
                    domain,
                    Files.probeContentType(localFile),
                    inputStream
            ).close();
            return BackfillOutcome.UPLOADED;
        } catch (IOException | RuntimeException ignored) {
            // Runtime read compatibility must still succeed from the local file.
            return BackfillOutcome.FAILED;
        }
    }

    private boolean objectExists(String domain, String logicalKey) {
        try {
            s3Client().headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket().trim())
                    .key(objectKey(domain, logicalKey))
                    .build());
            return true;
        } catch (RuntimeException ex) {
            if (isMissingObject(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private boolean isMissingObject(RuntimeException ex) {
        if (ex instanceof NoSuchKeyException) {
            return true;
        }
        if (ex instanceof S3Exception s3Exception) {
            if (s3Exception.statusCode() == 404) {
                return true;
            }
            return s3Exception.awsErrorDetails() != null
                    && "NoSuchKey".equalsIgnoreCase(s3Exception.awsErrorDetails().errorCode());
        }
        return false;
    }

    private String probeContentType(Path target, String fallbackMimeType) throws IOException {
        String detected = Files.probeContentType(target);
        if (StringUtils.hasText(detected)) {
            return detected;
        }
        return StringUtils.hasText(fallbackMimeType) ? fallbackMimeType.trim() : "application/octet-stream";
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
                    .region(Region.of(StringUtils.hasText(properties.getRegion()) ? properties.getRegion().trim() : "us-east-1"))
                    .forcePathStyle(properties.isPathStyleAccess());
            if (StringUtils.hasText(properties.getEndpoint())) {
                builder.endpointOverride(URI.create(properties.getEndpoint().trim()));
            }
            if (StringUtils.hasText(properties.getAccessKey()) || StringUtils.hasText(properties.getSecretKey())) {
                builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        StringUtils.hasText(properties.getAccessKey()) ? properties.getAccessKey().trim() : "",
                        StringUtils.hasText(properties.getSecretKey()) ? properties.getSecretKey().trim() : ""
                )));
            }
            s3Client = builder.build();
            return s3Client;
        }
    }

    private Path ensureDirectory(String directory) throws IOException {
        Path path = Paths.get(directory).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path;
    }

    public record StoredBinary(String logicalKey,
                               String contentType,
                               long size,
                               InputStream inputStream) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private enum BackfillOutcome {
        UPLOADED,
        ALREADY_PRESENT,
        FAILED
    }
}
