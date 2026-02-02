package com.example.panel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public class TicketId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ticket_id")
    private String ticketId;

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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TicketId ticketId1 = (TicketId) o;
        if (userId != null ? !userId.equals(ticketId1.userId) : ticketId1.userId != null) {
            return false;
        }
        return ticketId != null ? ticketId.equals(ticketId1.ticketId) : ticketId1.ticketId == null;
    }

    @Override
    public int hashCode() {
        int result = userId != null ? userId.hashCode() : 0;
        result = 31 * result + (ticketId != null ? ticketId.hashCode() : 0);
        return result;
    }
}
