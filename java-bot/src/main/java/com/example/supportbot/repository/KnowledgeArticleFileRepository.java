package com.example.supportbot.repository;

import com.example.supportbot.entity.KnowledgeArticleFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeArticleFileRepository extends JpaRepository<KnowledgeArticleFile, Long> {

    List<KnowledgeArticleFile> findByDraftToken(String draftToken);

    List<KnowledgeArticleFile> findByArticleId(Long articleId);
}
