package com.example.panel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bot_users")
@Getter
@Setter
public class BotUser {

    @Id
    private Long userId;

    private String username;

    private String firstName;

    private String lastName;

    private OffsetDateTime registeredAt;
}
