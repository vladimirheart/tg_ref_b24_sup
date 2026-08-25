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
@Table(name = "public_ingress_monitors")
public class PublicIngressMonitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_name", nullable = false)
    private String monitorName;

    @Column(name = "endpoint_url", nullable = false)
    private String endpointUrl;

    @Column(name = "scheme", nullable = false)
    private String scheme;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "expected_http_status")
    private Integer expectedHttpStatus;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "last_status")
    private String lastStatus;

    @Column(name = "last_summary")
    private String lastSummary;

    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "last_dns_resolved_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastDnsResolvedAt;

    @Column(name = "last_dns_addresses")
    private String lastDnsAddresses;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_http_duration_ms")
    private Long lastHttpDurationMs;

    @Column(name = "last_http_checked_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastHttpCheckedAt;

    @Column(name = "last_tls_checked_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastTlsCheckedAt;

    @Column(name = "last_tls_expires_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastTlsExpiresAt;

    @Column(name = "last_tls_days_left")
    private Integer lastTlsDaysLeft;

    @Column(name = "last_checked_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastCheckedAt;

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

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Integer getExpectedHttpStatus() {
        return expectedHttpStatus;
    }

    public void setExpectedHttpStatus(Integer expectedHttpStatus) {
        this.expectedHttpStatus = expectedHttpStatus;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public String getLastSummary() {
        return lastSummary;
    }

    public void setLastSummary(String lastSummary) {
        this.lastSummary = lastSummary;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public OffsetDateTime getLastDnsResolvedAt() {
        return lastDnsResolvedAt;
    }

    public void setLastDnsResolvedAt(OffsetDateTime lastDnsResolvedAt) {
        this.lastDnsResolvedAt = lastDnsResolvedAt;
    }

    public String getLastDnsAddresses() {
        return lastDnsAddresses;
    }

    public void setLastDnsAddresses(String lastDnsAddresses) {
        this.lastDnsAddresses = lastDnsAddresses;
    }

    public Integer getLastHttpStatus() {
        return lastHttpStatus;
    }

    public void setLastHttpStatus(Integer lastHttpStatus) {
        this.lastHttpStatus = lastHttpStatus;
    }

    public Long getLastHttpDurationMs() {
        return lastHttpDurationMs;
    }

    public void setLastHttpDurationMs(Long lastHttpDurationMs) {
        this.lastHttpDurationMs = lastHttpDurationMs;
    }

    public OffsetDateTime getLastHttpCheckedAt() {
        return lastHttpCheckedAt;
    }

    public void setLastHttpCheckedAt(OffsetDateTime lastHttpCheckedAt) {
        this.lastHttpCheckedAt = lastHttpCheckedAt;
    }

    public OffsetDateTime getLastTlsCheckedAt() {
        return lastTlsCheckedAt;
    }

    public void setLastTlsCheckedAt(OffsetDateTime lastTlsCheckedAt) {
        this.lastTlsCheckedAt = lastTlsCheckedAt;
    }

    public OffsetDateTime getLastTlsExpiresAt() {
        return lastTlsExpiresAt;
    }

    public void setLastTlsExpiresAt(OffsetDateTime lastTlsExpiresAt) {
        this.lastTlsExpiresAt = lastTlsExpiresAt;
    }

    public Integer getLastTlsDaysLeft() {
        return lastTlsDaysLeft;
    }

    public void setLastTlsDaysLeft(Integer lastTlsDaysLeft) {
        this.lastTlsDaysLeft = lastTlsDaysLeft;
    }

    public OffsetDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(OffsetDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
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
