package com.example.supportbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "client_blacklist")
public class ClientBlacklist {

    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "is_blacklisted", nullable = false)
    private boolean blacklisted;

    @Column(name = "unblock_requested", nullable = false)
    private boolean unblockRequested;

    @Column(name = "unblock_requested_at")
    private OffsetDateTime unblockRequestedAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isBlacklisted() {
        return blacklisted;
    }

    public void setBlacklisted(boolean blacklisted) {
        this.blacklisted = blacklisted;
    }

    public boolean isUnblockRequested() {
        return unblockRequested;
    }

    public void setUnblockRequested(boolean unblockRequested) {
        this.unblockRequested = unblockRequested;
    }

    public OffsetDateTime getUnblockRequestedAt() {
        return unblockRequestedAt;
    }

    public void setUnblockRequestedAt(OffsetDateTime unblockRequestedAt) {
        this.unblockRequestedAt = unblockRequestedAt;
    }
}