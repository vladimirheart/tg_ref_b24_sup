package com.example.supportbot.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "task_seq")
public class TaskSequence {

    @Id
    private Integer id;

    private Integer val;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getVal() {
        return val;
    }

    public void setVal(Integer val) {
        this.val = val;
    }
}
