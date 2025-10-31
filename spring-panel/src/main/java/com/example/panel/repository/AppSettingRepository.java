package com.example.panel.repository;

import com.example.panel.entity.AppSetting;
import com.example.panel.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {

    Optional<AppSetting> findByChannelAndKey(Channel channel, String key);

    List<AppSetting> findByChannel(Channel channel);
}
