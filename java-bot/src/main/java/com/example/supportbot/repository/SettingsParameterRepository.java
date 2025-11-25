package com.example.supportbot.repository;

import com.example.supportbot.entity.SettingsParameter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsParameterRepository extends JpaRepository<SettingsParameter, Long> {
}
