package com.example.panel.repository;

import com.example.panel.entity.KnowledgeArticleFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeArticleFileRepository extends JpaRepository<KnowledgeArticleFile, Long> {

    List<KnowledgeArticleFile> findByDraftToken(String draftToken);

    List<KnowledgeArticleFile> findByArticleId(Long articleId);
}
