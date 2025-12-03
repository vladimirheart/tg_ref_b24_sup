package com.example.panel.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a link between a task and a ticket using a composite key.
 */
@Entity
@Table(name = "task_links")
@Getter
@Setter
public class TaskLink {

    @EmbeddedId
    private TaskLinkId id;

    // task_id – часть PK и FK на Task
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("taskId") // связываем поле taskId из EmbeddedId с FK на Task
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    /**
     * Ticket привязан по ticket_id + user_id.
     * Эти колонки уже лежат в EmbeddedId (id.ticketId, id.userId),
     * поэтому здесь мы делаем их read-only, чтобы Hibernate
     * не ругался на дубликаты колонок.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(
                    name = "ticket_id",
                    referencedColumnName = "ticket_id",
                    insertable = false,
                    updatable = false
            ),
            @JoinColumn(
                    name = "user_id",
                    referencedColumnName = "user_id",
                    insertable = false,
                    updatable = false
            )
    })
    private Ticket ticket;

    // Необязательные, но удобные сеттеры для синхронизации id

    public void setTask(Task task) {
        this.task = task;
        if (task != null) {
            if (this.id == null) {
                this.id = new TaskLinkId();
            }
            // предполагаем, что у Task простой PK Long id
            this.id.setTaskId(task.getId());
        }
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
        if (ticket != null && ticket.getId() != null) {
            if (this.id == null) {
                this.id = new TaskLinkId();
            }
            this.id.setTicketId(ticket.getId().getTicketId());
            this.id.setUserId(ticket.getId().getUserId());
        }
    }
}
