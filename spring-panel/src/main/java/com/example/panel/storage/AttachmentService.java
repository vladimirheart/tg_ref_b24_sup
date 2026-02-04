package com.example.panel.storage;

import com.example.panel.service.PermissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AttachmentService {

    private final PermissionService permissionService;
    private final Path attachmentsRoot;
    private final Path knowledgeBaseRoot;
    private final Path avatarsRoot;

    public AttachmentService(PermissionService permissionService,
                              @Value("${app.storage.attachments:attachments}") String attachmentsDir,
                              @Value("${app.storage.knowledge-base:attachments/knowledge_base}") String knowledgeBaseDir,
                              @Value("${app.storage.avatars:attachments/avatars}") String avatarsDir) throws IOException {
        this.permissionService = permissionService;
        this.attachmentsRoot = ensureDirectory(attachmentsDir);
        this.knowledgeBaseRoot = ensureDirectory(knowledgeBaseDir);
        this.avatarsRoot = ensureDirectory(avatarsDir);
    }

    public ResponseEntity<Resource> downloadTicketAttachment(Authentication authentication, String ticketId, String filename) throws IOException {
        requireAuthority(authentication, "PAGE_DIALOGS");
        Path resolved = resolveAttachment(attachmentsRoot, ticketId, filename);
        return buildDownloadResponse(resolved, filename);
    }

    public ResponseEntity<Resource> downloadKnowledgeBaseFile(Authentication authentication, String fileId) throws IOException {
        requireAuthority(authentication, "PAGE_KNOWLEDGE_BASE");
        Path resolved = resolveAttachment(knowledgeBaseRoot, "", fileId);
        return buildDownloadResponse(resolved, resolved.getFileName().toString());
    }

    public ResponseEntity<Resource> downloadAvatar(Authentication authentication, String avatarId) throws IOException {
        requireAuthority(authentication, "PAGE_CLIENTS");
        Path resolved = resolveAttachment(avatarsRoot, "", avatarId);
        return buildDownloadResponse(resolved, resolved.getFileName().toString());
    }

    public AttachmentUploadMetadata storeKnowledgeBaseFile(Authentication authentication, MultipartFile file) throws IOException {
        requireAuthority(authentication, "PAGE_KNOWLEDGE_BASE");
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String safeName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file.bin");
        String storedName = UUID.randomUUID() + "_" + safeName;
        Path target = knowledgeBaseRoot.resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new AttachmentUploadMetadata(safeName, storedName, file.getSize(), OffsetDateTime.now());
    }

    public AttachmentUploadMetadata storeTicketAttachment(Authentication authentication, String ticketId, MultipartFile file) throws IOException {
        requireAuthority(authentication, "PAGE_DIALOGS");
        if (file.isEmpty() || !StringUtils.hasText(ticketId)) {
            throw new IllegalArgumentException("File is empty");
        }
        String safeName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file.bin");
        String storedName = UUID.randomUUID() + "_" + safeName;
        Path ticketDir = ensureDirectory(attachmentsRoot.resolve(ticketId).toString());
        Path target = ticketDir.resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new AttachmentUploadMetadata(safeName, storedName, file.getSize(), OffsetDateTime.now());
    }

    public void deleteKnowledgeBaseFile(Authentication authentication, String storedName) throws IOException {
        requireAuthority(authentication, "PAGE_KNOWLEDGE_BASE");
        Path target = knowledgeBaseRoot.resolve(storedName).normalize();
        if (!target.startsWith(knowledgeBaseRoot)) {
            throw new IllegalArgumentException("Invalid path");
        }
        Files.deleteIfExists(target);
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
        MediaType mediaType = MediaTypeFactory.detect(file);
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(file));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + downloadName)
                .contentType(mediaType)
                .contentLength(Files.size(file))
                .body(resource);
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

    private static final class MediaTypeFactory {
        private static MediaType detect(Path file) throws IOException {
            String probe = Files.probeContentType(file);
            return probe != null ? MediaType.parseMediaType(probe) : MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public record AttachmentUploadMetadata(String originalName, String storedName, long size, OffsetDateTime uploadedAt) {}
}
