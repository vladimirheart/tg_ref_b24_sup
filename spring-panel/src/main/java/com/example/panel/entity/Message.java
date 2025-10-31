package com.example.panel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "messages")
@Getter
@Setter
public class Message {

    @Id
    @Column(name = "group_msg_id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String business;

    private String locationType;

    private String city;

    private String locationName;

    private String problem;

    private OffsetDateTime createdAt;

    private String username;

    private String category;

    private String ticketId;

    private LocalDate createdDate;

    private String createdTime;

    private String clientName;

    private String clientStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    private OffsetDateTime updatedAt;

    private String updatedBy;
}
