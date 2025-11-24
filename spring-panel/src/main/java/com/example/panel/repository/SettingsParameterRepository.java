package com.example.panel.repository;

import com.example.panel.entity.SettingsParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettingsParameterRepository extends JpaRepository<SettingsParameter, Long> {

    List<SettingsParameter> findByParamType(String paramType);
}