package com.example.panel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "channels")
@Getter
@Setter
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    private String botName;

    @Column(nullable = false)
    private String channelName;

    private String questionsCfg;

    private Integer maxQuestions;

    private Boolean isActive;

    private OffsetDateTime createdAt;

    private String botUsername;

    private String questionTemplateId;

    private String ratingTemplateId;

    private String publicId;
}