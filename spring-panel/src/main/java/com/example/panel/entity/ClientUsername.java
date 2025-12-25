package com.example.panel.entity;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "client_usernames")
@Getter
@Setter
public class ClientUsername {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String username;

    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime seenAt;
}
