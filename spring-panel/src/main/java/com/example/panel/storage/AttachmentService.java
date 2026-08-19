package com.example.panel.storage;

import com.example.panel.service.PermissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AttachmentService {

    private final PermissionService permissionService;
    private final AttachmentObjectStorageService objectStorageService;
    private final Path attachmentsRoot;
    private final Path knowledgeBaseRoot;
    public AttachmentService(PermissionService permissionService,
                              AttachmentObjectStorageService objectStorageService,
                              @Value("${app.storage.attachments:attachments}") String attachmentsDir,
                              @Value("${app.storage.knowledge-base:attachments/knowledge_base}") String knowledgeBaseDir) throws IOException {
        this.permissionService = permissionService;
        this.objectStorageService = objectStorageService;
        this.attachmentsRoot = ensureDirectory(attachmentsDir);
        this.knowledgeBaseRoot = ensureDirectory(knowledgeBaseDir);
    }

    public ResponseEntity<Resource> downloadTicketAttachment(Authentication authentication, String ticketId, String filename) throws IOException {
        requireAuthority(authentication, "PAGE_DIALOGS");
        AttachmentObjectStorageService.StoredBinary binary = objectStorageService.openDialogAttachment(ticketId, filename);
        return buildResponse(binary, buildContentDisposition("attachment", filename));
    }


    public ResponseEntity<Resource> downloadTicketAttachmentByPath(Authentication authentication, String path) throws IOException {
        requireAuthority(authentication, "PAGE_DIALOGS");
        Path resolved = resolveByStoredPath(attachmentsRoot, path);
        return buildInlineResponse(resolved);
    }

    public ResponseEntity<Resource> downloadTicketAttachmentByStorageKey(Authentication authentication, String storageKey) throws IOException {
        requireAuthority(authentication, "PAGE_DIALOGS");
        AttachmentObjectStorageService.StoredBinary binary = objectStorageService.openDialogAttachmentByStorageKey(storageKey);
        String filename = AttachmentStorageKeyResolver.extractFileName(storageKey);
        return buildResponse(binary, buildContentDisposition("inline", StringUtils.hasText(filename) ? filename : "file"));
    }

    public ResponseEntity<Resource> downloadKnowledgeBaseFile(Authentication authentication, String fileId) throws IOException {
        requireAuthority(authentication, "PAGE_KNOWLEDGE_BASE");
        AttachmentObjectStorageService.StoredBinary binary = objectStorageService.openKnowledgeBaseFile(fileId);
        return buildResponse(binary, buildContentDisposition("attachment", fileId));
    }

    public ResponseEntity<Resource> downloadAvatar(Authentication authentication, String avatarId) throws IOException {
        requireAuthenticated(authentication);
        AttachmentObjectStorageService.StoredBinary binary = objectStorageService.openAvatar(avatarId);
        return buildResponse(binary, buildContentDisposition("inline", avatarId));
    }

    public AttachmentUploadMetadata storeKnowledgeBaseFile(Authentication authentication, MultipartFile file) throws IOException {
        requireAuthority(authentication, "PAGE_KNOWLEDGE_BASE");
        return storeKnowledgeBaseFileInternal(
            null,
            file.getOriginalFilename(),
            file.getContentType(),
            file.getInputStream(),
            file.isEmpty()
        );
    }

    public AttachmentUploadMetadata storeImportedKnowledgeBaseFile(String preferredStoredName,
                                                                   String originalName,
                                                                   String mimeType,
                                                                   InputStream inputStream) throws IOException {
        return storeKnowledgeBaseFileInternal(preferredStoredName, originalName, mimeType, inputStream, false);
    }

    public void deleteKnowledgeBaseFile(String storedName) throws IOException {
        deleteKnowledgeBaseFileInternal(storedName);
    }

    public AttachmentUploadMetadata storeTicketAttachment(Authentication authentication, String ticketId, MultipartFile file) throws IOException {
        requireAuthority(authentication, "PAGE_DIALOGS");
        if (file.isEmpty() || !StringUtils.hasText(ticketId)) {
            throw new IllegalArgumentException("File is empty");
        }
        String safeName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file.bin");
        String storedName = UUID.randomUUID() + "_" + safeName;
        try (InputStream in = file.getInputStream()) {
            AttachmentObjectStorageService.StoredBinary binary = objectStorageService.storeDialogAttachment(
                    ticketId,
                    storedName,
                    file.getContentType(),
                    in
            );
            return new AttachmentUploadMetadata(
                    safeName,
                    storedName,
                    binary.contentType(),
                    binary.size(),
                    OffsetDateTime.now()
            );
        }
    }

    public void deleteTicketAttachment(String ticketId, String storedName) throws IOException {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(storedName)) {
            return;
        }
        objectStorageService.deleteDialogAttachment(ticketId, storedName);
    }

    public void deleteKnowledgeBaseFile(Authentication authentication, String storedName) throws IOException {
        requireAuthority(authentication, "PAGE_KNOWLEDGE_BASE");
        deleteKnowledgeBaseFileInternal(storedName);
    }

    public AttachmentDescriptor describeTicketAttachment(String ticketId, String storedName) throws IOException {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(storedName)) {
            throw new IllegalArgumentException("File not found");
        }
        try (AttachmentObjectStorageService.StoredBinary binary = objectStorageService.openDialogAttachment(ticketId, storedName)) {
            return new AttachmentDescriptor(extractOriginalAttachmentName(storedName), binary.size());
        }
    }

    public AttachmentDescriptor describeTicketAttachmentByPath(String rawPath) throws IOException {
        Path resolved = resolveByStoredPath(attachmentsRoot, rawPath);
        return describeResolvedAttachment(resolved);
    }

    public AttachmentDescriptor describeTicketAttachmentByStorageKey(String storageKey) throws IOException {
        try (AttachmentObjectStorageService.StoredBinary binary = objectStorageService.openDialogAttachmentByStorageKey(storageKey)) {
            return new AttachmentDescriptor(
                    extractOriginalAttachmentName(AttachmentStorageKeyResolver.extractFileName(storageKey)),
                    binary.size()
            );
        }
    }

    public boolean hasTicketAttachmentByStorageKey(String storageKey) {
        try {
            return objectStorageService.dialogAttachmentExistsByStorageKey(storageKey);
        } catch (Exception ex) {
            return false;
        }
    }

    private AttachmentUploadMetadata storeKnowledgeBaseFileInternal(String preferredStoredName,
                                                                    String originalName,
                                                                    String mimeType,
                                                                    InputStream inputStream,
                                                                    boolean empty) throws IOException {
        if (empty || inputStream == null) {
            throw new IllegalArgumentException("File is empty");
        }
        String safeName = StringUtils.cleanPath(StringUtils.hasText(originalName) ? originalName : "file.bin");
        String storedName = StringUtils.hasText(preferredStoredName)
            ? StringUtils.cleanPath(preferredStoredName)
            : UUID.randomUUID() + "_" + safeName;
        try (InputStream in = inputStream) {
            AttachmentObjectStorageService.StoredBinary binary = objectStorageService.storeKnowledgeBaseFile(storedName, mimeType, in);
            return new AttachmentUploadMetadata(
                    safeName,
                    storedName,
                    binary.contentType(),
                    binary.size(),
                    OffsetDateTime.now()
            );
        }
    }

    private void deleteKnowledgeBaseFileInternal(String storedName) throws IOException {
        if (!StringUtils.hasText(storedName)) {
            return;
        }
        objectStorageService.deleteKnowledgeBaseFile(storedName);
    }

    private String probeContentType(Path target, String fallbackMimeType) throws IOException {
        String detected = Files.probeContentType(target);
        if (StringUtils.hasText(detected)) {
            return detected;
        }
        return StringUtils.hasText(fallbackMimeType) ? fallbackMimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    public void purgeDraftAttachments(String prefix) throws IOException {
        try (var stream = Files.list(knowledgeBaseRoot)) {
            stream.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .forEach(path -> {
                        try {
                            FileSystemUtils.deleteRecursively(path);
                        } catch (IOException ex) {
                            throw new UncheckedIOException("Failed to delete " + path, ex);
                        }
                    });
        }
    }

    private ResponseEntity<Resource> buildDownloadResponse(Path file, String downloadName) throws IOException {
        return buildResponse(file, buildContentDisposition("attachment", downloadName));
    }

    private ResponseEntity<Resource> buildInlineResponse(Path file) throws IOException {
        String filename = file.getFileName() != null ? file.getFileName().toString() : "file";
        return buildResponse(file, buildContentDisposition("inline", filename));
    }

    private ResponseEntity<Resource> buildResponse(Path file, String disposition) throws IOException {
        MediaType mediaType = MediaTypeFactory.detect(file);
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(file));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(mediaType)
                .contentLength(Files.size(file))
                .body(resource);
    }

    private ResponseEntity<Resource> buildResponse(AttachmentObjectStorageService.StoredBinary binary, String disposition) {
        MediaType mediaType = StringUtils.hasText(binary.contentType())
                ? MediaType.parseMediaType(binary.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        InputStreamResource resource = new InputStreamResource(binary.inputStream());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(mediaType)
                .contentLength(binary.size())
                .body(resource);
    }

    private String buildContentDisposition(String type, String filename) {
        String safeFilename = StringUtils.hasText(filename) ? filename.trim() : "file";
        return ContentDisposition.builder(type)
                .filename(safeFilename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    private Path resolveAttachment(Path root, String ticketId, String filename) {
        Path base = StringUtils.hasText(ticketId) ? root.resolve(ticketId) : root;
        Path resolved = base.resolve(filename).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid path");
        }
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("File not found");
        }
        return resolved;
    }

    private Path resolveTicketAttachmentPath(String ticketId, String filename) {
        Path ticketDir = attachmentsRoot.resolve(ticketId).normalize();
        Path resolved = ticketDir.resolve(filename).normalize();
        if (!resolved.startsWith(attachmentsRoot)) {
            throw new IllegalArgumentException("Invalid path");
        }
        return resolved;
    }

    private AttachmentDescriptor describeResolvedAttachment(Path resolved) throws IOException {
        String filename = resolved.getFileName() != null ? resolved.getFileName().toString() : "file";
        return new AttachmentDescriptor(extractOriginalAttachmentName(filename), Files.size(resolved));
    }

    private String extractOriginalAttachmentName(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "file";
        }
        String normalized = filename.trim();
        int separatorIndex = normalized.indexOf('_');
        if (separatorIndex > 0) {
            String prefix = normalized.substring(0, separatorIndex);
            if (prefix.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                return normalized.substring(separatorIndex + 1);
            }
        }
        return normalized;
    }



    private Path resolveByStoredPath(Path root, String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            throw new IllegalArgumentException("File not found");
        }
        String normalized = rawPath.trim().replace('\\', '/');
        if (normalized.contains("/attachments/")) {
            try {
                Path originalPath = Paths.get(rawPath.trim()).toAbsolutePath().normalize();
                if (Files.exists(originalPath) && Files.isRegularFile(originalPath)) {
                    return originalPath;
                }
            } catch (Exception ignored) {
                // Fallback to relative resolution against attachments root.
            }
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String[] segments = normalized.split("/");
        int attachmentsIndex = -1;
        for (int i = 0; i < segments.length; i++) {
            if ("attachments".equalsIgnoreCase(segments[i])) {
                attachmentsIndex = i;
                break;
            }
        }
        if (attachmentsIndex >= 0) {
            normalized = String.join("/", java.util.Arrays.copyOfRange(segments, attachmentsIndex + 1, segments.length));
        }
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("File not found");
        }
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root) || !Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("File not found");
        }
        return resolved;
    }

    private Path ensureDirectory(String directory) throws IOException {
        Path path = Paths.get(directory).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path;
    }

    private void requireAuthority(Authentication authentication, String authority) {
        if (!permissionService.hasAuthority(authentication, authority)) {
            throw new SecurityException("Forbidden");
        }
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("Forbidden");
        }
    }

    private static final class MediaTypeFactory {
        private static MediaType detect(Path file) throws IOException {
            String probe = Files.probeContentType(file);
            return probe != null ? MediaType.parseMediaType(probe) : MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public record AttachmentUploadMetadata(String originalName,
                                           String storedName,
                                           String mimeType,
                                           long size,
                                           OffsetDateTime uploadedAt) {}

    public record AttachmentDescriptor(String originalName, long size) {}
}
