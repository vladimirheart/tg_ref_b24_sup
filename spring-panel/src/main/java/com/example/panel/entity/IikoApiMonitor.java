package com.example.panel.entity;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "iiko_api_monitors")
public class IikoApiMonitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_name", nullable = false)
    private String monitorName;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "api_login", nullable = false)
    private String apiLogin;

    @Column(name = "request_type", nullable = false)
    private String requestType;

    @Column(name = "request_config_json")
    private String requestConfigJson;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "locations_sync_enabled", nullable = false)
    private Boolean locationsSyncEnabled;

    @Column(name = "last_status")
    private String lastStatus;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "last_duration_ms")
    private Long lastDurationMs;

    @Column(name = "last_checked_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastCheckedAt;

    @Column(name = "last_token_checked_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastTokenCheckedAt;

    @Column(name = "last_response_excerpt")
    private String lastResponseExcerpt;

    @Column(name = "last_response_summary_json")
    private String lastResponseSummaryJson;

    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures;

    @Column(name = "created_at", nullable = false)
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMonitorName() {
        return monitorName;
    }

    public void setMonitorName(String monitorName) {
        this.monitorName = monitorName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiLogin() {
        return apiLogin;
    }

    public void setApiLogin(String apiLogin) {
        this.apiLogin = apiLogin;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getRequestConfigJson() {
        return requestConfigJson;
    }

    public void setRequestConfigJson(String requestConfigJson) {
        this.requestConfigJson = requestConfigJson;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getLocationsSyncEnabled() {
        return locationsSyncEnabled;
    }

    public void setLocationsSyncEnabled(Boolean locationsSyncEnabled) {
        this.locationsSyncEnabled = locationsSyncEnabled;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public Integer getLastHttpStatus() {
        return lastHttpStatus;
    }

    public void setLastHttpStatus(Integer lastHttpStatus) {
        this.lastHttpStatus = lastHttpStatus;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public Long getLastDurationMs() {
        return lastDurationMs;
    }

    public void setLastDurationMs(Long lastDurationMs) {
        this.lastDurationMs = lastDurationMs;
    }

    public OffsetDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(OffsetDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public OffsetDateTime getLastTokenCheckedAt() {
        return lastTokenCheckedAt;
    }

    public void setLastTokenCheckedAt(OffsetDateTime lastTokenCheckedAt) {
        this.lastTokenCheckedAt = lastTokenCheckedAt;
    }

    public String getLastResponseExcerpt() {
        return lastResponseExcerpt;
    }

    public void setLastResponseExcerpt(String lastResponseExcerpt) {
        this.lastResponseExcerpt = lastResponseExcerpt;
    }

    public String getLastResponseSummaryJson() {
        return lastResponseSummaryJson;
    }

    public void setLastResponseSummaryJson(String lastResponseSummaryJson) {
        this.lastResponseSummaryJson = lastResponseSummaryJson;
    }

    public Integer getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(Integer consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
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
}
