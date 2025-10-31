package com.example.panel.repository;

import com.example.panel.entity.ClientUsername;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientUsernameRepository extends JpaRepository<ClientUsername, Long> {

    List<ClientUsername> findByUserIdOrderBySeenAtDesc(Long userId);
}
