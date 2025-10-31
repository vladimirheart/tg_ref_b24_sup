package com.example.supportbot.repository;

import com.example.supportbot.entity.ClientBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientBlacklistRepository extends JpaRepository<ClientBlacklist, String> {
}
