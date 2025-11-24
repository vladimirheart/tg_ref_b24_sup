package com.example.panel.service;

import com.example.panel.entity.KnowledgeArticle;
import com.example.panel.entity.KnowledgeArticleFile;
import com.example.panel.model.knowledge.KnowledgeArticleCommand;
import com.example.panel.model.knowledge.KnowledgeArticleDetails;
import com.example.panel.model.knowledge.KnowledgeArticleSummary;
import com.example.panel.model.knowledge.KnowledgeAttachmentDto;
import com.example.panel.repository.KnowledgeArticleFileRepository;
import com.example.panel.repository.KnowledgeArticleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class KnowledgeBaseService {

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeArticleFileRepository fileRepository;

    public KnowledgeBaseService(KnowledgeArticleRepository articleRepository,
                                KnowledgeArticleFileRepository fileRepository) {
        this.articleRepository = articleRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeArticleSummary> listArticles() {
        return articleRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .map(article -> new KnowledgeArticleSummary(
                        article.getId(),
                        article.getTitle(),
                        article.getDepartment(),
                        article.getStatus(),
                        article.getAuthor(),
                        article.getSummary(),
                        article.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeArticleDetails> findArticle(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return articleRepository.findById(id).map(article -> toDetails(article, fileRepository.findByArticleId(id)));
    }

    public KnowledgeArticleDetails saveArticle(KnowledgeArticleCommand command) {
        KnowledgeArticle article = command.id() != null
                ? articleRepository.findById(command.id()).orElseGet(KnowledgeArticle::new)
                : new KnowledgeArticle();
        OffsetDateTime now = OffsetDateTime.now();
        if (article.getCreatedAt() == null) {
            article.setCreatedAt(now);
        }
        article.setUpdatedAt(now);
        article.setTitle(trim(command.title()));
        article.setDepartment(trim(command.department()));
        article.setArticleType(trim(command.articleType()));
        article.setStatus(trim(command.status()));
        article.setAuthor(trim(command.author()));
        article.setDirection(trim(command.direction()));
        article.setDirectionSubtype(trim(command.directionSubtype()));
        article.setSummary(trim(command.summary()));
        article.setContent(command.content());
        KnowledgeArticle saved = articleRepository.save(article);
        List<KnowledgeArticleFile> attachments = fileRepository.findByArticleId(saved.getId());
        return toDetails(saved, attachments);
    }

    private KnowledgeArticleDetails toDetails(KnowledgeArticle article, List<KnowledgeArticleFile> files) {
        List<KnowledgeAttachmentDto> attachments = files.stream()
                .map(file -> new KnowledgeAttachmentDto(
                        file.getId(),
                        file.getOriginalName(),
                        file.getStoredPath(),
                        file.getMimeType(),
                        file.getFileSize(),
                        file.getUploadedAt()
                ))
                .toList();
        return new KnowledgeArticleDetails(
                article.getId(),
                article.getTitle(),
                article.getDepartment(),
                article.getArticleType(),
                article.getStatus(),
                article.getAuthor(),
                article.getDirection(),
                article.getDirectionSubtype(),
                article.getSummary(),
                article.getContent(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                attachments
        );
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}