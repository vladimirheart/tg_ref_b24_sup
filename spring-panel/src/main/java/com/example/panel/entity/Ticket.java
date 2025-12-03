package com.example.panel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tickets")
@Getter
@Setter
public class Ticket {

    @EmbeddedId
    private TicketId id;

    @Column(name = "group_msg_id")
    private Long groupMessageId;

    private String status;

    private OffsetDateTime resolvedAt;

    private String resolvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    private Integer reopenCount;

    private Integer closedCount;

    private Long workTimeTotalSec;

    private OffsetDateTime lastReopenAt;

    public Long getUserId() {
        return id != null ? id.getUserId() : null;
    }

    public String getTicketId() {
        return id != null ? id.getTicketId() : null;
    }
}
