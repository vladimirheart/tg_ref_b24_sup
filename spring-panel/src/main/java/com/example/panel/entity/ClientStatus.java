package com.example.panel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "client_statuses")
@Getter
@Setter
public class ClientStatus {

    @Id
    private Long userId;

    private String status;

    private OffsetDateTime updatedAt;

    private String updatedBy;
}