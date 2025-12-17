package com.example.panel.entity;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tasks")
@Getter
@Setter
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long seq;

    private String source;

    private String title;

    private String bodyHtml;

    private String creator;

    private String assignee;

    private String tag;

    private String status;

    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime dueAt;

    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime createdAt;

    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime closedAt;

    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime lastActivityAt;
}
