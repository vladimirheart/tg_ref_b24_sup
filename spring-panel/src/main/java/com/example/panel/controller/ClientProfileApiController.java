package com.example.panel.controller;

import com.example.panel.entity.ClientPhone;
import com.example.panel.entity.ClientStatus;
import com.example.panel.repository.ClientPhoneRepository;
import com.example.panel.repository.ClientStatusRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAuthority('PAGE_CLIENTS')")
public class ClientProfileApiController {

    private static final Logger log = LoggerFactory.getLogger(ClientProfileApiController.class);

    private final JdbcTemplate jdbcTemplate;
    private final ClientStatusRepository clientStatusRepository;
    private final ClientPhoneRepository clientPhoneRepository;

    public ClientProfileApiController(JdbcTemplate jdbcTemplate,
                                      ClientStatusRepository clientStatusRepository,
                                      ClientPhoneRepository clientPhoneRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.clientStatusRepository = clientStatusRepository;
        this.clientPhoneRepository = clientPhoneRepository;
    }

    @PostMapping("/{userId}/name")
    @Transactional
    public Map<String, Object> updateName(@PathVariable("userId") long userId,
                                          @RequestBody Map<String, Object> payload) {
        String name = payload.get("client_name") != null ? String.valueOf(payload.get("client_name")) : "";
        String trimmed = name == null ? "" : name.trim();
        jdbcTemplate.update("UPDATE messages SET client_name = ? WHERE user_id = ?", trimmed, userId);
        log.info("Updated client name for {} to '{}'", userId, trimmed);
        return Map.of("ok", true, "client_name", trimmed);
    }

    @PostMapping("/{userId}/status")
    @Transactional
    public Map<String, Object> updateStatus(@PathVariable("userId") long userId,
                                            @RequestBody Map<String, Object> payload,
                                            Authentication authentication) {
        String status = payload.get("client_status") != null ? String.valueOf(payload.get("client_status")) : "";
        String trimmed = StringUtils.hasText(status) ? status.trim() : null;
        ClientStatus entry = clientStatusRepository.findById(userId).orElseGet(() -> {
            ClientStatus fresh = new ClientStatus();
            fresh.setUserId(userId);
            return fresh;
        });
        entry.setStatus(trimmed);
        entry.setUpdatedAt(OffsetDateTime.now());
        entry.setUpdatedBy(authentication != null ? authentication.getName() : "system");
        clientStatusRepository.save(entry);
        log.info("Updated client status for {} to '{}'", userId, trimmed);
        return Map.of("ok", true, "client_status", trimmed);
    }

    @PostMapping("/{userId}/phones")
    @Transactional
    public Map<String, Object> addPhone(@PathVariable("userId") long userId,
                                        @RequestBody Map<String, Object> payload,
                                        Authentication authentication) {
        String phone = payload.get("phone") != null ? String.valueOf(payload.get("phone")) : "";
        if (!StringUtils.hasText(phone)) {
            return Map.of("ok", false, "error", "Введите номер телефона");
        }
        String label = payload.get("label") != null ? String.valueOf(payload.get("label")) : null;
        ClientPhone entry = new ClientPhone();
        entry.setUserId(userId);
        entry.setPhone(phone.trim());
        entry.setLabel(StringUtils.hasText(label) ? label.trim() : null);
        entry.setSource("manual");
        entry.setActive(true);
        entry.setCreatedAt(OffsetDateTime.now());
        entry.setCreatedBy(authentication != null ? authentication.getName() : "system");
        ClientPhone saved = clientPhoneRepository.save(entry);
        log.info("Added manual phone {} for {}", saved.getId(), userId);
        return Map.of(
            "ok", true,
            "id", saved.getId(),
            "phone", saved.getPhone(),
            "label", saved.getLabel(),
            "created_at", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null
        );
    }

    @PatchMapping("/{userId}/phones/{phoneId}")
    @Transactional
    public Map<String, Object> updatePhone(@PathVariable("userId") long userId,
                                           @PathVariable("phoneId") long phoneId,
                                           @RequestBody Map<String, Object> payload) {
        Optional<ClientPhone> entryOpt = clientPhoneRepository.findById(phoneId);
        if (entryOpt.isEmpty()) {
            return Map.of("ok", false, "error", "Телефон не найден");
        }
        ClientPhone entry = entryOpt.get();
        if (!userIdEquals(entry.getUserId(), userId)) {
            return Map.of("ok", false, "error", "Телефон не принадлежит клиенту");
        }
        if (payload.containsKey("label")) {
            String label = payload.get("label") != null ? String.valueOf(payload.get("label")) : null;
            entry.setLabel(StringUtils.hasText(label) ? label.trim() : null);
        }
        if (payload.containsKey("active")) {
            entry.setActive(Boolean.parseBoolean(String.valueOf(payload.get("active"))));
        }
        clientPhoneRepository.save(entry);
        return Map.of("ok", true);
    }

    private boolean userIdEquals(Long stored, long incoming) {
        return stored != null && stored == incoming;
    }
}
