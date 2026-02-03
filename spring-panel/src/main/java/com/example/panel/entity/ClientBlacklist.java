package com.example.panel.entity;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "client_blacklist")
public class ClientBlacklist {

    @Id
    private String userId;

    @Column(name = "is_blacklisted")
    private Boolean blacklisted;

    private String reason;

    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime addedAt;

    private String addedBy;

    @Column(name = "unblock_requested")
    private Boolean unblockRequested;

    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime unblockRequestedAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Boolean getBlacklisted() {
        return blacklisted;
    }

    public void setBlacklisted(Boolean blacklisted) {
        this.blacklisted = blacklisted;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(OffsetDateTime addedAt) {
        this.addedAt = addedAt;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public Boolean getUnblockRequested() {
        return unblockRequested;
    }

    public void setUnblockRequested(Boolean unblockRequested) {
        this.unblockRequested = unblockRequested;
    }

    public OffsetDateTime getUnblockRequestedAt() {
        return unblockRequestedAt;
    }

    public void setUnblockRequestedAt(OffsetDateTime unblockRequestedAt) {
        this.unblockRequestedAt = unblockRequestedAt;
    }
}
