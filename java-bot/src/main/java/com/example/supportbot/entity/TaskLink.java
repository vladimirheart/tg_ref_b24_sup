package com.example.supportbot.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "task_links")
public class TaskLink {

    @EmbeddedId
    private TaskLinkId id;

    public TaskLink() {
    }

    public TaskLink(TaskLinkId id) {
        this.id = id;
    }

    public TaskLink(Long taskId, String ticketId) {
        this.id = new TaskLinkId(taskId, ticketId);
    }

    public TaskLinkId getId() {
        return id;
    }

    public void setId(TaskLinkId id) {
        this.id = id;
    }
}
