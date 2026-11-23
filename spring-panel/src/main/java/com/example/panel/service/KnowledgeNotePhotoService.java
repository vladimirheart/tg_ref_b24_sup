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

/** Note photos share the notes' PAGE_KNOWLEDGE_BASE visibility, including editor previews. */
@Service
public class KnowledgeNotePhotoService {
    static final String PURPOSE = "knowledge-note-photo-v1";
    private static final String PREFIX = "/api/attachments/knowledge-base/";
    private static final Pattern PHOTO = Pattern.compile("!\\[[^\\]\\r\\n]*\\]\\((/api/attachments/knowledge-base/[a-zA-Z0-9%_.~-]+)\\)");
    private final JdbcTemplate jdbc;
    private final AttachmentService attachments;
    private final KnowledgeAccessService access;

    public KnowledgeNotePhotoService(JdbcTemplate jdbc, AttachmentService attachments, KnowledgeAccessService access) {
        this.jdbc = jdbc; this.attachments = attachments; this.access = access;
    }

    public AttachmentService.AttachmentUploadMetadata upload(Authentication authentication, MultipartFile file) throws IOException {
        if (authentication == null || authentication.getAuthorities().stream().noneMatch(a -> "PAGE_KNOWLEDGE_BASE".equals(a.getAuthority()))
                || !access.canCreateArticle(authentication)) throw new AccessDeniedException("Нет доступа к фотографиям заметок");
        if (!isPhotoName(file.getOriginalFilename())) throw new IllegalArgumentException("Выберите PNG, JPEG, GIF или WebP");
        var metadata = attachments.storeKnowledgeBaseFile(authentication, file);
        try {
            jdbc.update("""
                INSERT INTO knowledge_article_files(draft_token, stored_path, original_name, mime_type, file_size, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, PURPOSE, metadata.storedName(), metadata.originalName(), metadata.mimeType(), metadata.size(), metadata.uploadedAt());
        } catch (RuntimeException failure) {
            try { attachments.deleteKnowledgeBaseFile(authentication, metadata.storedName()); }
            catch (IOException | RuntimeException ignored) { /* Keep the registration failure; never expose binary or credentials. */ }
            throw failure;
        }
        return metadata;
    }

    static boolean isPhotoName(String name) {
        return name != null && !name.contains("..") && !Pattern.compile("[\\\\/\\x00-\\x1f?#]").matcher(name).find()
            && name.toLowerCase(Locale.ROOT).matches(".+\\.(png|jpe?g|gif|webp)");
    }

    /** Match the same protected raster-image syntax as knowledge-note-content.js, never a substring. */
    static boolean references(String body, String storedName) {
        if (body == null || !isPhotoName(storedName)) return false;
        var matches = PHOTO.matcher(body);
        while (matches.find()) {
            try {
                String decoded = URLDecoder.decode(matches.group(1).substring(PREFIX.length()), StandardCharsets.UTF_8);
                if (isPhotoName(decoded) && storedName.equals(decoded)) return true;
            } catch (IllegalArgumentException invalid) { /* Malformed percent encoding is not a photo reference. */ }
        }
        return false;
    }
}
