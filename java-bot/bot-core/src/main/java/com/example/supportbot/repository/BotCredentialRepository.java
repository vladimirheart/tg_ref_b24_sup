package com.example.supportbot.repository;

import com.example.supportbot.entity.BotCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotCredentialRepository extends JpaRepository<BotCredential, Long> {
}
