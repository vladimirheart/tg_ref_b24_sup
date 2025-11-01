package com.example.panel.repository;

import com.example.panel.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByToken(String token);

    Optional<Channel> findByPublicId(String publicId);

    Optional<Channel> findByPublicIdIgnoreCase(String publicId);
}
