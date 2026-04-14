package com.example.supportbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TaskLinkId implements Serializable {

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "ticket_id")
    private String ticketId;

    public TaskLinkId() {
    }

    public TaskLinkId(Long taskId, String ticketId) {
        this.taskId = taskId;
        this.ticketId = ticketId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
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
        TaskLinkId that = (TaskLinkId) o;
        return Objects.equals(taskId, that.taskId) && Objects.equals(ticketId, that.ticketId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, ticketId);
    }
}
