package com.example.panel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket_spans")
@Getter
@Setter
public class TicketSpan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ticketId;

    private Integer spanNo;

    private OffsetDateTime startedAt;

    private OffsetDateTime endedAt;

    private Long durationSeconds;
}
