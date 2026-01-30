package com.example.supportbot.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket_active")
public class TicketActive {

    @Id
    @Column(name = "ticket_id")
    private String ticketId;

    @Column(name = "user_identity")
    private String user;

    @Column(name = "last_seen")
    private OffsetDateTime lastSeen;

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public OffsetDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(OffsetDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }
}
