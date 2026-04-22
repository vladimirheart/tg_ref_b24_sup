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
@Table(name = "rms_license_monitors")
public class RmsLicenseMonitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rms_address", nullable = false)
    private String rmsAddress;

    @Column(name = "scheme", nullable = false)
    private String scheme;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "auth_login", nullable = false)
    private String authLogin;

    @Column(name = "auth_password", nullable = false)
    private String authPassword;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "license_monitoring_enabled", nullable = false)
    private Boolean licenseMonitoringEnabled;

    @Column(name = "network_monitoring_enabled", nullable = false)
    private Boolean networkMonitoringEnabled;

    @Column(name = "server_name")
    private String serverName;

    @Column(name = "server_type")
    private String serverType;

    @Column(name = "server_version")
    private String serverVersion;

    @Column(name = "license_status")
    private String licenseStatus;

    @Column(name = "license_error_message")
    private String licenseErrorMessage;

    @Column(name = "license_expires_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime licenseExpiresAt;

    @Column(name = "license_days_left")
    private Integer licenseDaysLeft;

    @Column(name = "license_last_checked_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime licenseLastCheckedAt;

    @Column(name = "license_last_notified_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime licenseLastNotifiedAt;

    @Column(name = "rms_status")
    private String rmsStatus;

    @Column(name = "rms_status_message")
    private String rmsStatusMessage;

    @Column(name = "ping_output")
    private String pingOutput;

    @Column(name = "traceroute_summary")
    private String tracerouteSummary;

    @Column(name = "traceroute_report")
    private String tracerouteReport;

    @Column(name = "traceroute_checked_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime tracerouteCheckedAt;

    @Column(name = "rms_last_checked_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime rmsLastCheckedAt;

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

    public String getRmsAddress() {
        return rmsAddress;
    }

    public void setRmsAddress(String rmsAddress) {
        this.rmsAddress = rmsAddress;
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

    public String getAuthLogin() {
        return authLogin;
    }

    public void setAuthLogin(String authLogin) {
        this.authLogin = authLogin;
    }

    public String getAuthPassword() {
        return authPassword;
    }

    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getLicenseMonitoringEnabled() {
        return licenseMonitoringEnabled;
    }

    public void setLicenseMonitoringEnabled(Boolean licenseMonitoringEnabled) {
        this.licenseMonitoringEnabled = licenseMonitoringEnabled;
    }

    public Boolean getNetworkMonitoringEnabled() {
        return networkMonitoringEnabled;
    }

    public void setNetworkMonitoringEnabled(Boolean networkMonitoringEnabled) {
        this.networkMonitoringEnabled = networkMonitoringEnabled;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public String getLicenseStatus() {
        return licenseStatus;
    }

    public void setLicenseStatus(String licenseStatus) {
        this.licenseStatus = licenseStatus;
    }

    public String getLicenseErrorMessage() {
        return licenseErrorMessage;
    }

    public void setLicenseErrorMessage(String licenseErrorMessage) {
        this.licenseErrorMessage = licenseErrorMessage;
    }

    public OffsetDateTime getLicenseExpiresAt() {
        return licenseExpiresAt;
    }

    public void setLicenseExpiresAt(OffsetDateTime licenseExpiresAt) {
        this.licenseExpiresAt = licenseExpiresAt;
    }

    public Integer getLicenseDaysLeft() {
        return licenseDaysLeft;
    }

    public void setLicenseDaysLeft(Integer licenseDaysLeft) {
        this.licenseDaysLeft = licenseDaysLeft;
    }

    public OffsetDateTime getLicenseLastCheckedAt() {
        return licenseLastCheckedAt;
    }

    public void setLicenseLastCheckedAt(OffsetDateTime licenseLastCheckedAt) {
        this.licenseLastCheckedAt = licenseLastCheckedAt;
    }

    public OffsetDateTime getLicenseLastNotifiedAt() {
        return licenseLastNotifiedAt;
    }

    public void setLicenseLastNotifiedAt(OffsetDateTime licenseLastNotifiedAt) {
        this.licenseLastNotifiedAt = licenseLastNotifiedAt;
    }

    public String getRmsStatus() {
        return rmsStatus;
    }

    public void setRmsStatus(String rmsStatus) {
        this.rmsStatus = rmsStatus;
    }

    public String getRmsStatusMessage() {
        return rmsStatusMessage;
    }

    public void setRmsStatusMessage(String rmsStatusMessage) {
        this.rmsStatusMessage = rmsStatusMessage;
    }

    public String getPingOutput() {
        return pingOutput;
    }

    public void setPingOutput(String pingOutput) {
        this.pingOutput = pingOutput;
    }

    public String getTracerouteSummary() {
        return tracerouteSummary;
    }

    public void setTracerouteSummary(String tracerouteSummary) {
        this.tracerouteSummary = tracerouteSummary;
    }

    public String getTracerouteReport() {
        return tracerouteReport;
    }

    public void setTracerouteReport(String tracerouteReport) {
        this.tracerouteReport = tracerouteReport;
    }

    public OffsetDateTime getTracerouteCheckedAt() {
        return tracerouteCheckedAt;
    }

    public void setTracerouteCheckedAt(OffsetDateTime tracerouteCheckedAt) {
        this.tracerouteCheckedAt = tracerouteCheckedAt;
    }

    public OffsetDateTime getRmsLastCheckedAt() {
        return rmsLastCheckedAt;
    }

    public void setRmsLastCheckedAt(OffsetDateTime rmsLastCheckedAt) {
        this.rmsLastCheckedAt = rmsLastCheckedAt;
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
