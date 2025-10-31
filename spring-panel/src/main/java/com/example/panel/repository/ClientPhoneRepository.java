package com.example.panel.repository;

import com.example.panel.entity.ClientPhone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientPhoneRepository extends JpaRepository<ClientPhone, Long> {

    List<ClientPhone> findByUserId(Long userId);
}
