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
@Table(name = "smtp_notification_monitors")
public class SmtpNotificationMonitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_name", nullable = false)
    private String monitorName;

    @Column(name = "relay_host", nullable = false)
    private String relayHost;

    @Column(name = "relay_port", nullable = false)
    private Integer relayPort;

    @Column(name = "protocol_mode", nullable = false)
    private String protocolMode;

    @Column(name = "connect_timeout_ms", nullable = false)
    private Integer connectTimeoutMs;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "last_status")
    private String lastStatus;

    @Column(name = "last_summary")
    private String lastSummary;

    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "last_banner")
    private String lastBanner;

    @Column(name = "last_tls_protocol")
    private String lastTlsProtocol;

    @Column(name = "last_tls_cipher_suite")
    private String lastTlsCipherSuite;

    @Column(name = "last_connected_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastConnectedAt;

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

    public String getRelayHost() {
        return relayHost;
    }

    public void setRelayHost(String relayHost) {
        this.relayHost = relayHost;
    }

    public Integer getRelayPort() {
        return relayPort;
    }

    public void setRelayPort(Integer relayPort) {
        this.relayPort = relayPort;
    }

    public String getProtocolMode() {
        return protocolMode;
    }

    public void setProtocolMode(String protocolMode) {
        this.protocolMode = protocolMode;
    }

    public Integer getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(Integer connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
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

    public String getLastBanner() {
        return lastBanner;
    }

    public void setLastBanner(String lastBanner) {
        this.lastBanner = lastBanner;
    }

    public String getLastTlsProtocol() {
        return lastTlsProtocol;
    }

    public void setLastTlsProtocol(String lastTlsProtocol) {
        this.lastTlsProtocol = lastTlsProtocol;
    }

    public String getLastTlsCipherSuite() {
        return lastTlsCipherSuite;
    }

    public void setLastTlsCipherSuite(String lastTlsCipherSuite) {
        this.lastTlsCipherSuite = lastTlsCipherSuite;
    }

    public OffsetDateTime getLastConnectedAt() {
        return lastConnectedAt;
    }

    public void setLastConnectedAt(OffsetDateTime lastConnectedAt) {
        this.lastConnectedAt = lastConnectedAt;
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
