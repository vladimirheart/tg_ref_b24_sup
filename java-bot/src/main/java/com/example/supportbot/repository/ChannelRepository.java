package com.example.supportbot.repository;

import com.example.supportbot.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findByToken(String token);
    Optional<Channel> findByPublicId(String publicId);
}
