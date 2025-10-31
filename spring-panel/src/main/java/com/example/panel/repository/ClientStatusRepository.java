package com.example.panel.repository;

import com.example.panel.entity.ClientStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientStatusRepository extends JpaRepository<ClientStatus, Long> {
}
