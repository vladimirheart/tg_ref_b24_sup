package com.example.panel.entity;␊
␊
import jakarta.persistence.EmbeddedId;␊
import jakarta.persistence.Entity;␊
import jakarta.persistence.FetchType;␊
import jakarta.persistence.JoinColumn;␊
import jakarta.persistence.JoinColumns;␊
import jakarta.persistence.ManyToOne;␊
import jakarta.persistence.MapsId;␊
import jakarta.persistence.Table;␊
import lombok.Getter;␊
import lombok.Setter;␊
␊
@Entity␊
@Table(name = "task_links")␊
@Getter␊
@Setter␊
public class TaskLink {␊
␊
    @EmbeddedId␊
    private TaskLinkId id;␊
␊
    @ManyToOne(fetch = FetchType.LAZY)␊
    @MapsId("taskId")␊
    @JoinColumn(name = "task_id")␊
    private Task task;␊
␊
    @ManyToOne(fetch = FetchType.LAZY)␊
    @MapsId("ticketId")␊
    @JoinColumns({␊
            @JoinColumn(name = "ticket_id", referencedColumnName = "ticket_id"),␊
            @JoinColumn(name = "user_id", referencedColumnName = "user_id", insertable = false, updatable = false)
    })␊
    private Ticket ticket;␊
}␊
