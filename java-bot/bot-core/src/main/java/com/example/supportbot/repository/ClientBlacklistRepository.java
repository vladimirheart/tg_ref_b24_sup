package com.example.supportbot.repository;

import com.example.supportbot.entity.ClientBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface ClientBlacklistRepository extends JpaRepository<ClientBlacklist, String> {
    List<ClientBlacklist> findByUserIdIn(Collection<String> userIds);
}
