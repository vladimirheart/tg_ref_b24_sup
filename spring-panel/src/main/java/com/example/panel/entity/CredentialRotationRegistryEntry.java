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
@Table(name = "credential_rotation_registry")
public class CredentialRotationRegistryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_key", nullable = false)
    private String entryKey;

    @Column(name = "integration_kind", nullable = false)
    private String integrationKind;

    @Column(name = "credential_kind", nullable = false)
    private String credentialKind;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_ref", nullable = false)
    private String sourceRef;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "note")
    private String note;

    @Column(name = "source_present", nullable = false)
    private Boolean sourcePresent;

    @Column(name = "secret_present", nullable = false)
    private Boolean secretPresent;

    @Column(name = "last_status")
    private String lastStatus;

    @Column(name = "status_level")
    private String statusLevel;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "expires_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime expiresAt;

    @Column(name = "rotated_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime rotatedAt;

    @Column(name = "rotation_interval_days")
    private Integer rotationIntervalDays;

    @Column(name = "next_rotation_due_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime nextRotationDueAt;

    @Column(name = "last_seen_at")
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastSeenAt;

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

    public String getEntryKey() {
        return entryKey;
    }

    public void setEntryKey(String entryKey) {
        this.entryKey = entryKey;
    }

    public String getIntegrationKind() {
        return integrationKind;
    }

    public void setIntegrationKind(String integrationKind) {
        this.integrationKind = integrationKind;
    }

    public String getCredentialKind() {
        return credentialKind;
    }

    public void setCredentialKind(String credentialKind) {
        this.credentialKind = credentialKind;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Boolean getSourcePresent() {
        return sourcePresent;
    }

    public void setSourcePresent(Boolean sourcePresent) {
        this.sourcePresent = sourcePresent;
    }

    public Boolean getSecretPresent() {
        return secretPresent;
    }

    public void setSecretPresent(Boolean secretPresent) {
        this.secretPresent = secretPresent;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public String getStatusLevel() {
        return statusLevel;
    }

    public void setStatusLevel(String statusLevel) {
        this.statusLevel = statusLevel;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getRotatedAt() {
        return rotatedAt;
    }

    public void setRotatedAt(OffsetDateTime rotatedAt) {
        this.rotatedAt = rotatedAt;
    }

    public Integer getRotationIntervalDays() {
        return rotationIntervalDays;
    }

    public void setRotationIntervalDays(Integer rotationIntervalDays) {
        this.rotationIntervalDays = rotationIntervalDays;
    }

    public OffsetDateTime getNextRotationDueAt() {
        return nextRotationDueAt;
    }

    public void setNextRotationDueAt(OffsetDateTime nextRotationDueAt) {
        this.nextRotationDueAt = nextRotationDueAt;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(OffsetDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
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
