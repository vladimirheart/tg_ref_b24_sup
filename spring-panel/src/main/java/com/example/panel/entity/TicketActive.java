package com.example.panel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket_active")
@Getter
@Setter
public class TicketActive {

    @Id
    private String ticketId;

    private String userIdentity;

    private OffsetDateTime lastSeen;
}