package com.example.panel.repository;

import com.example.panel.entity.KnowledgeArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {

    Optional<KnowledgeArticle> findFirstByExternalSourceAndExternalId(String externalSource, String externalId);

    List<KnowledgeArticle> findAllByExternalSourceAndExternalIdIn(String externalSource, Collection<String> externalIds);

    List<KnowledgeArticle> findAllByExternalSource(String externalSource);
}
