package com.example.panel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket_responsibles")
@Getter
@Setter
public class TicketResponsible {

    @Id
    private String ticketId;

    private String responsible;

    private OffsetDateTime assignedAt;

    private String assignedBy;
}
