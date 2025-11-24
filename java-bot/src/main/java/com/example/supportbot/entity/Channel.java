package com.example.supportbot.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "channels")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", length = 128)
    private String token;

    @Column(name = "bot_name", length = 255)
    private String botName;

    @Column(name = "bot_username", length = 255)
    private String botUsername;

    @Column(name = "channel_name", nullable = false, length = 255)
    private String channelName = "Telegram";

    @Column(name = "questions_cfg", columnDefinition = "TEXT")
    private String questionsCfg = "{}";

    @Column(name = "max_questions")
    private Integer maxQuestions = 0;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "question_template_id", length = 255)
    private String questionTemplateId;

    @Column(name = "rating_template_id", length = 255)
    private String ratingTemplateId;

    @Column(name = "auto_action_template_id", length = 255)
    private String autoActionTemplateId;

    @Column(name = "public_id", length = 64)
    private String publicId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "filters", columnDefinition = "TEXT")
    private String filters = "{}";

    @Column(name = "delivery_settings", columnDefinition = "TEXT")
    private String deliverySettings = "{}";

    @Column(name = "platform", nullable = false, length = 64)
    private String platform = "telegram";

    @Column(name = "platform_config", columnDefinition = "TEXT")
    private String platformConfig = "{}";

    @Column(name = "credential_id")
    private Long credentialId;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "support_chat_id", length = 255)
    private String supportChatId;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (channelName == null || channelName.isBlank()) {
            channelName = "Telegram";
        }
        if (platform == null || platform.isBlank()) {
            platform = "telegram";
        }
        if (filters == null || filters.isBlank()) {
            filters = "{}";
        }
        if (deliverySettings == null || deliverySettings.isBlank()) {
            deliverySettings = "{}";
        }
        if (questionsCfg == null || questionsCfg.isBlank()) {
            questionsCfg = "{}";
        }
        if (maxQuestions == null) {
            maxQuestions = 0;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getBotName() {
        return botName;
    }

    public void setBotName(String botName) {
        this.botName = botName;
    }

    public String getBotUsername() {
        return botUsername;
    }

    public void setBotUsername(String botUsername) {
        this.botUsername = botUsername;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getQuestionsCfg() {
        return questionsCfg;
    }

    public void setQuestionsCfg(String questionsCfg) {
        this.questionsCfg = questionsCfg;
    }

    public Integer getMaxQuestions() {
        return maxQuestions;
    }

    public void setMaxQuestions(Integer maxQuestions) {
        this.maxQuestions = maxQuestions;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getQuestionTemplateId() {
        return questionTemplateId;
    }

    public void setQuestionTemplateId(String questionTemplateId) {
        this.questionTemplateId = questionTemplateId;
    }

    public String getRatingTemplateId() {
        return ratingTemplateId;
    }

    public void setRatingTemplateId(String ratingTemplateId) {
        this.ratingTemplateId = ratingTemplateId;
    }

    public String getAutoActionTemplateId() {
        return autoActionTemplateId;
    }

    public void setAutoActionTemplateId(String autoActionTemplateId) {
        this.autoActionTemplateId = autoActionTemplateId;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFilters() {
        return filters;
    }

    public void setFilters(String filters) {
        this.filters = filters;
    }

    public String getDeliverySettings() {
        return deliverySettings;
    }

    public void setDeliverySettings(String deliverySettings) {
        this.deliverySettings = deliverySettings;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getPlatformConfig() {
        return platformConfig;
    }

    public void setPlatformConfig(String platformConfig) {
        this.platformConfig = platformConfig;
    }

    public Long getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(Long credentialId) {
        this.credentialId = credentialId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSupportChatId() {
        return supportChatId;
    }

    public void setSupportChatId(String supportChatId) {
        this.supportChatId = supportChatId;
    }
}
