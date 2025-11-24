package com.example.panel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "task_seq")
@Getter
@Setter
public class TaskSequence {

    @Id
    private Integer id;

    private Long val;
}