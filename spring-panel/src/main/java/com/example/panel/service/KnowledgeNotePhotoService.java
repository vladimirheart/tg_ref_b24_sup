package com.example.panel.service;

import com.example.panel.storage.AttachmentService;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Notes' photos and downloadable attachments share PAGE_KNOWLEDGE_BASE visibility. */
@Service
public class KnowledgeNotePhotoService {
    static final String PURPOSE = "knowledge-note-photo-v1";
    static final String FILE_PURPOSE = "knowledge-note-file-v1";
    private static final String PREFIX = "/api/attachments/knowledge-base/";
    private static final Pattern REFERENCE = Pattern.compile("(!?)\\[[^\\]\\r\\n]*\\]\\((/api/attachments/knowledge-base/[a-zA-Z0-9%_.~-]+)\\)");
    private static final Pattern UNSAFE_NAME = Pattern.compile("[\\\\/\\x00-\\x1f?#]");
    private final JdbcTemplate jdbc;
    private final AttachmentService attachments;
    private final KnowledgeAccessService access;

    public KnowledgeNotePhotoService(JdbcTemplate jdbc, AttachmentService attachments, KnowledgeAccessService access) {
        this.jdbc = jdbc; this.attachments = attachments; this.access = access;
    }

    public AttachmentService.AttachmentUploadMetadata upload(Authentication authentication, MultipartFile file) throws IOException {
        requireActor(authentication);
        if (!isPhotoName(file.getOriginalFilename())) throw new IllegalArgumentException("Выберите PNG, JPEG, GIF или WebP");
        return store(authentication, file, PURPOSE);
    }

    public AttachmentService.AttachmentUploadMetadata uploadFile(Authentication authentication, MultipartFile file) throws IOException {
        requireActor(authentication);
        if (!isAttachmentName(file.getOriginalFilename())) throw new IllegalArgumentException("Недопустимое имя файла");
        return store(authentication, file, FILE_PURPOSE);
    }

    private void requireActor(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream().noneMatch(a -> "PAGE_KNOWLEDGE_BASE".equals(a.getAuthority()))
                || !access.canCreateArticle(authentication)) throw new AccessDeniedException("Нет доступа к вложениям заметок");
    }

    private AttachmentService.AttachmentUploadMetadata store(Authentication authentication, MultipartFile file, String purpose) throws IOException {
        long maxBytes = (isPhotoName(file.getOriginalFilename()) ? 10L : 50L) * 1024 * 1024;
        if (file.isEmpty() || file.getSize() > maxBytes) throw new IllegalArgumentException("Фотографии — до 10 МБ, другие файлы — до 50 МБ");
        var metadata = attachments.storeKnowledgeBaseFile(authentication, file);
        try {
            jdbc.update("""
                INSERT INTO knowledge_article_files(draft_token, stored_path, original_name, mime_type, file_size, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, purpose, metadata.storedName(), metadata.originalName(), metadata.mimeType(), metadata.size(), metadata.uploadedAt());
        } catch (RuntimeException failure) {
            try { attachments.deleteKnowledgeBaseFile(authentication, metadata.storedName()); }
            catch (IOException | RuntimeException ignored) { /* Keep the registration failure; never expose binary or credentials. */ }
            throw failure;
        }
        return metadata;
    }

    static boolean isPhotoName(String name) {
        return isAttachmentName(name)
            && name.toLowerCase(Locale.ROOT).matches(".+\\.(png|jpe?g|gif|webp)");
    }

    static boolean isAttachmentName(String name) {
        return name != null && !name.isBlank() && !name.contains("..") && !UNSAFE_NAME.matcher(name).find();
    }

    /** Match protected photo/file links exactly; arbitrary text never grants file access. */
    static boolean references(String body, String storedName) {
        if (body == null || !isAttachmentName(storedName)) return false;
        var matches = REFERENCE.matcher(body);
        while (matches.find()) {
            try {
                String decoded = URLDecoder.decode(matches.group(2).substring(PREFIX.length()), StandardCharsets.UTF_8);
                if (isAttachmentName(decoded) && (matches.group(1).isEmpty() || isPhotoName(decoded)) && storedName.equals(decoded)) return true;
            } catch (IllegalArgumentException invalid) { /* Malformed percent encoding is not a photo reference. */ }
        }
        return false;
    }
}
