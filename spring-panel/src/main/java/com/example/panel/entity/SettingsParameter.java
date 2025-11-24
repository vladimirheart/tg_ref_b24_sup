package com.example.panel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "settings_parameters")
@Getter
@Setter
public class SettingsParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String paramType;

    private String value;

    private OffsetDateTime createdAt;

    private String state;

    @Column(name = "is_deleted")
    private Boolean deleted;

    private OffsetDateTime deletedAt;

    private String extraJson;
}