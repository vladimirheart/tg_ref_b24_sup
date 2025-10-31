package com.example.panel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class TaskLinkId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "ticket_id")
    private String ticketId;
}
