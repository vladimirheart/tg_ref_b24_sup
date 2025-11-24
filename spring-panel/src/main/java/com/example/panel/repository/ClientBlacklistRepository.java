package com.example.panel.repository;

import com.example.panel.entity.ClientBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientBlacklistRepository extends JpaRepository<ClientBlacklist, String> {
}