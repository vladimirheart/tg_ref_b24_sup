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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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

    public String getBotUsername() {
        return botUsername;
    }

    public void setBotUsername(String botUsername) {
        this.botUsername = botUsername;
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

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getAutoActionTemplateId() {
        return autoActionTemplateId;
    }

    public void setAutoActionTemplateId(String autoActionTemplateId) {
        this.autoActionTemplateId = autoActionTemplateId;
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

    public String getSupportChatId() {
        return supportChatId;
    }

    public void setSupportChatId(String supportChatId) {
        this.supportChatId = supportChatId;
    }
}
