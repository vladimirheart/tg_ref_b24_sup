package com.example.panel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "panel_users")
@Getter
@Setter
public class PanelUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    private String passwordHash;

    private String password;

    private String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role roleRef;

    private String photo;

    private OffsetDateTime registrationDate;

    private LocalDate birthDate;

    private String email;

    private String department;

    private String phones;

    private String fullName;

    @Column(name = "is_blocked")
    private Boolean blocked;
}
