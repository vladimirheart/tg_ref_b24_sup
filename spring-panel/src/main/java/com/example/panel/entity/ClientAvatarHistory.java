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
@Table(name = "client_avatar_history")
@Getter
@Setter
public class ClientAvatarHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String fingerprint;

    private String source;

    private String fileUniqueId;

    private String fileId;

    private String thumbPath;

    private String fullPath;

    private Integer width;

    private Integer height;

    private Long fileSize;

    private OffsetDateTime fetchedAt;

    private OffsetDateTime lastSeenAt;

    private String metadata;
}