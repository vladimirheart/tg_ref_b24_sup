package com.example.supportbot.repository;

import com.example.supportbot.entity.ClientStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientStatusRepository extends JpaRepository<ClientStatus, Long> {
}
