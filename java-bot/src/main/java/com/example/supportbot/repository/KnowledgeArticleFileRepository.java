package com.example.supportbot.repository;

import com.example.supportbot.entity.KnowledgeArticleFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeArticleFileRepository extends JpaRepository<KnowledgeArticleFile, Long> {
}
