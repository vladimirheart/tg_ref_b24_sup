package com.example.panel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "client_blacklist")
@Getter
@Setter
public class ClientBlacklist {

    @Id
    private String userId;

    @Column(name = "is_blacklisted")
    private Boolean blacklisted;

    private String reason;

    private OffsetDateTime addedAt;

    private String addedBy;

    @Column(name = "unblock_requested")
    private Boolean unblockRequested;

    private OffsetDateTime unblockRequestedAt;
}