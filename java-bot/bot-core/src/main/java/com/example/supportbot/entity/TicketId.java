package com.example.supportbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TicketId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ticket_id")
    private String ticketId;

    public TicketId() {
    }

    public TicketId(Long userId, String ticketId) {
        this.userId = userId;
        this.ticketId = ticketId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TicketId ticketId1 = (TicketId) o;
        return Objects.equals(userId, ticketId1.userId) && Objects.equals(ticketId, ticketId1.ticketId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, ticketId);
    }
}
