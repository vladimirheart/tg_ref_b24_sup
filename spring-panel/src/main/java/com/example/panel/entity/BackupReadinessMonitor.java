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
@Table(name = "backup_readiness_monitors")
public class BackupReadinessMonitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_name", nullable = false)
    private String monitorName;

    @Column(name = "backup_kind", nullable = false)
    private String backupKind;

    @Column(name = "path_pattern", nullable = false)
    private String pathPattern;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "freshness_threshold_hours", nullable = false)
    private Integer freshnessThresholdHours;

    @Column(name = "restore_threshold_days", nullable = false)
    private Integer restoreThresholdDays;

    @Column(name = "last_status")
    private String lastStatus;

    @Column(name = "last_summary")
    private String lastSummary;

    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "last_backup_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastBackupAt;

    @Column(name = "last_backup_size_bytes")
    private Long lastBackupSizeBytes;

    @Column(name = "last_backup_path")
    private String lastBackupPath;

    @Column(name = "last_restore_verified_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastRestoreVerifiedAt;

    @Column(name = "last_restore_note")
    private String lastRestoreNote;

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

    public String getBackupKind() {
        return backupKind;
    }

    public void setBackupKind(String backupKind) {
        this.backupKind = backupKind;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getFreshnessThresholdHours() {
        return freshnessThresholdHours;
    }

    public void setFreshnessThresholdHours(Integer freshnessThresholdHours) {
        this.freshnessThresholdHours = freshnessThresholdHours;
    }

    public Integer getRestoreThresholdDays() {
        return restoreThresholdDays;
    }

    public void setRestoreThresholdDays(Integer restoreThresholdDays) {
        this.restoreThresholdDays = restoreThresholdDays;
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

    public OffsetDateTime getLastBackupAt() {
        return lastBackupAt;
    }

    public void setLastBackupAt(OffsetDateTime lastBackupAt) {
        this.lastBackupAt = lastBackupAt;
    }

    public Long getLastBackupSizeBytes() {
        return lastBackupSizeBytes;
    }

    public void setLastBackupSizeBytes(Long lastBackupSizeBytes) {
        this.lastBackupSizeBytes = lastBackupSizeBytes;
    }

    public String getLastBackupPath() {
        return lastBackupPath;
    }

    public void setLastBackupPath(String lastBackupPath) {
        this.lastBackupPath = lastBackupPath;
    }

    public OffsetDateTime getLastRestoreVerifiedAt() {
        return lastRestoreVerifiedAt;
    }

    public void setLastRestoreVerifiedAt(OffsetDateTime lastRestoreVerifiedAt) {
        this.lastRestoreVerifiedAt = lastRestoreVerifiedAt;
    }

    public String getLastRestoreNote() {
        return lastRestoreNote;
    }

    public void setLastRestoreNote(String lastRestoreNote) {
        this.lastRestoreNote = lastRestoreNote;
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
