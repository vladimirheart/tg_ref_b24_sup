package com.example.panel.entity;

import jakarta.persistence.Column;
import com.example.panel.converter.LenientOffsetDateTimeConverter;
import jakarta.persistence.Convert;
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

    @Column(name = "is_active")
    private Boolean active;

    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime createdAt;

    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime updatedAt;

    private String botUsername;

    private String questionTemplateId;

    private String ratingTemplateId;

    private String publicId;

    private String autoActionTemplateId;

    private String description;

    private String filters;

    private String deliverySettings;

    private String platform;

    private String platformConfig;

    private Long credentialId;

    private String supportChatId;
}
