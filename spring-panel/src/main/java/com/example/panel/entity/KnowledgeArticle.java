package com.example.panel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "knowledge_articles")
@Getter
@Setter
public class KnowledgeArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String department;

    private String articleType;

    private String status;

    private String author;

    private String direction;

    private String directionSubtype;

    private String summary;

    private String content;

    private String attachments;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}