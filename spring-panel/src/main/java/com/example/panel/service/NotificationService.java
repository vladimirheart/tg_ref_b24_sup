package com.example.panel.service;

import com.example.panel.entity.Notification;
import com.example.panel.model.notification.NotificationDto;
import com.example.panel.model.notification.NotificationSummary;
import com.example.panel.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate usersJdbcTemplate;

    public NotificationService(NotificationRepository notificationRepository,
                               JdbcTemplate jdbcTemplate,
                               @Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate) {
        this.notificationRepository = notificationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.usersJdbcTemplate = usersJdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> findForUser(String userIdentity) {
        String identity = normalizeIdentity(userIdentity);
        return notificationRepository.findByUserIdentityOrderByCreatedAtDesc(identity).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationSummary summary(String userIdentity) {
        String identity = normalizeIdentity(userIdentity);
        long unread = notificationRepository.countByUserIdentityAndIsReadFalse(identity);
        return new NotificationSummary(unread);
    }

    public void markAsRead(String userIdentity, Long id) {
        String identity = normalizeIdentity(userIdentity);
        notificationRepository.findByIdAndUserIdentity(id, identity).ifPresent(notification -> {
            notification.setIsRead(Boolean.TRUE);
            notificationRepository.save(notification);
        });
    }

    public void notifyUser(String userIdentity, String text, String url) {
        String identity = normalizeRecipient(userIdentity);
        if (!StringUtils.hasText(identity) || !StringUtils.hasText(text)) {
            return;
        }
        saveNotification(identity, text.trim(), normalizeUrl(url));
    }

    public void notifyUsers(Set<String> userIdentities, String text, String url) {
        notifyUsersExcluding(userIdentities, null, text, url);
    }

    public void notifyUsersExcluding(Set<String> userIdentities, String excludedIdentity, String text, String url) {
        if (!StringUtils.hasText(text) || userIdentities == null || userIdentities.isEmpty()) {
            return;
        }
        String excluded = normalizeRecipient(excludedIdentity);
        String safeText = text.trim();
        String safeUrl = normalizeUrl(url);
        Set<String> recipients = normalizeRecipients(userIdentities);
        for (String identity : recipients) {
            if (StringUtils.hasText(excluded) && excluded.equals(identity)) {
                continue;
            }
            saveNotification(identity, safeText, safeUrl);
        }
    }

    @Transactional(readOnly = true)
    public Set<String> findDialogRecipients(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return Set.of();
        }
        Set<String> recipients = new LinkedHashSet<>();
        jdbcTemplate.query(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                rs -> {
                    while (rs.next()) {
                        recipients.addAll(splitIdentities(rs.getString("responsible")));
                    }
                },
                ticketId.trim()
        );
        jdbcTemplate.query(
                "SELECT user_identity FROM ticket_active WHERE ticket_id = ?",
                rs -> {
                    while (rs.next()) {
                        recipients.addAll(splitIdentities(rs.getString("user_identity")));
                    }
                },
                ticketId.trim()
        );
        return recipients;
    }

    public void notifyDialogParticipants(String ticketId, String text, String url, String excludedIdentity) {
        Set<String> recipients = findDialogRecipients(ticketId);
        if (recipients.isEmpty()) {
            notifyAllOperators(text, url, excludedIdentity);
            return;
        }
        notifyUsersExcluding(recipients, excludedIdentity, text, url);
    }

    public void notifyAllOperators(String text, String url, String excludedIdentity) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        notifyUsersExcluding(findAllOperatorRecipients(), excludedIdentity, text, url);
    }

    @Transactional(readOnly = true)
    public Set<String> findAllOperatorRecipients() {
        Set<String> recipients = new LinkedHashSet<>();
        recipients.addAll(loadOperatorRecipients(usersJdbcTemplate));
        recipients.addAll(loadOperatorRecipients(jdbcTemplate));
        return recipients;
    }

    private Set<String> loadOperatorRecipients(JdbcTemplate source) {
        if (source == null) {
            return Set.of();
        }
        Set<String> userColumns = loadUsersTableColumns(source);
        if (userColumns.isEmpty() || !userColumns.contains("username")) {
            return Set.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT username
                  FROM users
                 WHERE 1 = 1
                """);
        if (userColumns.contains("enabled")) {
            sql.append(" AND COALESCE(enabled, 1) = 1");
        }
        if (userColumns.contains("is_blocked")) {
            sql.append(" AND COALESCE(is_blocked, 0) = 0");
        }
        Set<String> recipients = new LinkedHashSet<>();
        try {
            source.query(sql.toString(), rs -> {
                while (rs.next()) {
                    String username = normalizeRecipient(rs.getString("username"));
                    if (StringUtils.hasText(username)) {
                        recipients.add(username);
                    }
                }
            });
        } catch (Exception ex) {
            return Set.of();
        }
        return recipients;
    }

    private Set<String> loadUsersTableColumns(JdbcTemplate source) {
        try {
            return new HashSet<>(source.query(
                    "PRAGMA table_info(users)",
                    (rs, rowNum) -> rs.getString("name")
            ));
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private NotificationDto toDto(Notification entity) {
        return new NotificationDto(
                entity.getId(),
                entity.getText(),
                entity.getUrl(),
                Boolean.TRUE.equals(entity.getIsRead()),
                entity.getCreatedAt()
        );
    }

    private String normalizeIdentity(String userIdentity) {
        return StringUtils.hasText(userIdentity)
                ? userIdentity.trim().toLowerCase(Locale.ROOT)
                : "all";
    }

    private String normalizeRecipient(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> splitIdentities(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        Set<String> identities = new LinkedHashSet<>();
        String[] chunks = raw.split("[,;\\s]+");
        for (String chunk : chunks) {
            String normalized = normalizeRecipient(chunk);
            if (StringUtils.hasText(normalized)) {
                identities.add(normalized);
            }
        }
        return identities;
    }

    private Set<String> normalizeRecipients(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String identity : raw) {
            String value = normalizeRecipient(identity);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String normalizeUrl(String url) {
        return StringUtils.hasText(url) ? url.trim() : null;
    }

    private void saveNotification(String identity, String text, String url) {
        Notification notification = new Notification();
        notification.setUserIdentity(identity);
        notification.setText(text);
        notification.setUrl(url);
        notification.setIsRead(Boolean.FALSE);
        notification.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        notificationRepository.save(notification);
    }
}
