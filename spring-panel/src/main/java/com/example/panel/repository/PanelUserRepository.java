package com.example.panel.repository;

import com.example.panel.entity.PanelUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PanelUserRepository extends JpaRepository<PanelUser, Long> {

    Optional<PanelUser> findByUsernameIgnoreCase(String username);
}