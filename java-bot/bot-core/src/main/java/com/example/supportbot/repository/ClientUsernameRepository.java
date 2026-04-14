package com.example.supportbot.repository;

import com.example.supportbot.entity.ClientUsername;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientUsernameRepository extends JpaRepository<ClientUsername, Long> {
}
