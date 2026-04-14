package com.example.supportbot.service;

import com.example.supportbot.entity.KnowledgeArticle;
import com.example.supportbot.entity.KnowledgeArticleFile;
import com.example.supportbot.repository.KnowledgeArticleFileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SharedStorageService {

    private final KnowledgeArticleFileRepository fileRepository;
    private final Path knowledgeBaseRoot;
    private final Path webFormRoot;

    public SharedStorageService(KnowledgeArticleFileRepository fileRepository,
                                @Value("${support-bot.knowledge-base-dir:attachments/knowledge_base}") String knowledgeDir,
                                @Value("${support-bot.webforms-dir:attachments/forms}") String webFormsDir) throws IOException {
        this.fileRepository = fileRepository;
        this.knowledgeBaseRoot = ensureDirectory(knowledgeDir);
        this.webFormRoot = ensureDirectory(webFormsDir);
    }

    public KnowledgeArticleFile storeKnowledgeAttachment(KnowledgeArticle article,
                                                         String draftToken,
                                                         String originalName,
                                                         String mimeType,
                                                         InputStream data) throws IOException {
        if (article == null && !StringUtils.hasText(draftToken)) {
            throw new IllegalArgumentException("Either article or draftToken is required");
        }
        String safeName = StringUtils.hasText(originalName) ? StringUtils.cleanPath(originalName) : "file.bin";
        String storedName = UUID.randomUUID() + "_" + safeName;
        Path target = knowledgeBaseRoot.resolve(storedName);
        Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);

        KnowledgeArticleFile file = new KnowledgeArticleFile();
        file.setArticle(article);
        file.setDraftToken(draftToken);
        file.setStoredPath(storedName);
        file.setOriginalName(safeName);
        file.setMimeType(mimeType);
        file.setFileSize(Files.size(target));
        file.setUploadedAt(OffsetDateTime.now());
        return fileRepository.save(file);
    }

    public Path storeWebFormAttachment(String sessionToken, String originalName, InputStream data) throws IOException {
        if (!StringUtils.hasText(sessionToken)) {
            throw new IllegalArgumentException("sessionToken is required");
        }
        String safeName = StringUtils.hasText(originalName) ? StringUtils.cleanPath(originalName) : "file.bin";
        String storedName = UUID.randomUUID() + "_" + safeName;
        Path dir = webFormRoot.resolve(sessionToken);
        Files.createDirectories(dir);
        Path target = dir.resolve(storedName);
        Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private Path ensureDirectory(String directory) throws IOException {
        Path path = Paths.get(directory).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path;
    }
}
