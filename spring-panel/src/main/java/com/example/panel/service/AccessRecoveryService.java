package com.example.panel.service;

import com.example.panel.security.*;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccessRecoveryService {
    public static final String RIGHT = "EDIT_ACCESS_RECOVER";
    public static final String CLIENT_UNBLOCK_RIGHT = "EDIT_CLIENT_UNBLOCK";
    public static final String PUBLIC_MESSAGE = "Если учётная запись существует или требует уточнения, запрос будет рассмотрен ответственным сотрудником.";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final PublicAuthenticationGuard guard;
    private final RequestClientAddressResolver addresses;
    private final AccessRecoveryProperties properties;
    private final UiEventStreamService events;

    public AccessRecoveryService(@Qualifier("usersJdbcTemplate") JdbcTemplate jdbc, PasswordEncoder passwords,
            PublicAuthenticationGuard guard, RequestClientAddressResolver addresses,
            AccessRecoveryProperties properties, UiEventStreamService events) {
        this.jdbc = jdbc; this.passwords = passwords; this.guard = guard;
        this.addresses = addresses; this.properties = properties; this.events = events;
    }

    @Transactional
    public void submit(Map<String, Object> payload, HttpServletRequest request) {
        String username = text(payload.get("username"), 255);
        String name = text(payload.get("full_name"), 200);
        String contact = text(payload.get("contact"), 255);
        String kind = text(payload.get("kind"), 32);
        if (!Set.of("password", "blocked", "other").contains(kind)) kind = "other";
        String dedupe = username.isBlank() ? "missing:" + PublicAuthenticationGuard.digest(name + "\u0000" + contact.toLowerCase(Locale.ROOT)) : "login:" + username.toLowerCase(Locale.ROOT);
        String ip = addresses.resolve(request);
        boolean allowed = guard.allow("recovery", ip, dedupe);
        if (!allowed || String.valueOf(payload.getOrDefault("username", "")).length() > 255
                || (username.isBlank() && (!"other".equals(kind) || name.isBlank() || contact.isBlank()))) return;
        var accounts = jdbc.queryForList("SELECT id, username FROM users WHERE lower(username) = lower(?) LIMIT 2", username);
        Map<String, Object> account = !username.isBlank() && accounts.size() == 1 ? accounts.get(0) : Map.of();
        Object userId = account.get("id");
        if (userId != null) dedupe = "account:" + userId;
        String lookup = accounts.size() > 1 ? "AMBIGUOUS" : userId == null ? "UNKNOWN" : "FOUND";
        // Both account states take the same write path. Anonymous repeats never overwrite identity evidence.
        Long id = jdbc.queryForObject("""
                INSERT INTO password_reset_requests(user_id, username_snapshot, requested_by_username,
                    requested_by_ip, connection_ip, requested_user_agent, requested_note, status, created_at,
                    request_kind, requester_name, requester_contact, correlation_id, lookup_result,
                    dedupe_key, last_requested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(dedupe_key) WHERE status IN ('PENDING', 'NEEDS_REVIEW')
                DO UPDATE SET repeat_count = LEAST(password_reset_requests.repeat_count + 1, 1000000),
                              last_requested_at = CURRENT_TIMESTAMP
                RETURNING id
                """, Long.class, userId, account.getOrDefault("username", username.isBlank() ? "Не указан" : username), username, ip,
                addresses.connectionAddress(request), text(request.getHeader("User-Agent"), 512),
                text(payload.get("comment"), 1000), userId == null ? "NEEDS_REVIEW" : "PENDING", kind,
                name, contact, UUID.randomUUID().toString(), lookup, dedupe);
        audit(id, "RECEIVED", "public", null);
        invalidateAfterCommit();
    }

    public static boolean canRecover(Authentication actor) {
        return actor != null && actor.isAuthenticated() && ("admin".equalsIgnoreCase(actor.getName()) || PanelAccessPolicy.has(actor, RIGHT));
    }
    public static boolean canUnblockClient(Authentication actor) {
        return actor != null && actor.isAuthenticated() && ("admin".equalsIgnoreCase(actor.getName()) || PanelAccessPolicy.has(actor, CLIENT_UNBLOCK_RIGHT));
    }
    public void requireRecovery(Authentication actor) {
        if (!canRecover(actor)) throw new AccessDeniedException("Нет права «Восстановить доступ»");
    }

    public List<Map<String, Object>> list(Authentication actor) {
        requireRecovery(actor);
        return jdbc.queryForList("""
                SELECT r.id, r.user_id, r.username_snapshot, r.requested_by_username, r.requested_by_ip,
                    r.connection_ip, r.requested_user_agent, r.requested_note, r.status, r.request_kind,
                    r.requester_name, r.requester_contact, r.correlation_id, r.lookup_result, r.repeat_count,
                    r.created_at, r.last_requested_at, r.resolution_note, r.resolved_at, r.resolved_by_username,
                    u.username AS linked_username, u.is_blocked AS account_blocked, u.enabled AS account_enabled
                FROM password_reset_requests r LEFT JOIN users u ON u.id = r.user_id
                ORDER BY CASE WHEN r.status IN ('PENDING','NEEDS_REVIEW') THEN 0 ELSE 1 END, r.id DESC LIMIT 300
                """);
    }
    public List<Map<String, Object>> history(long id, Authentication actor) {
        requireRecovery(actor);
        return jdbc.queryForList("SELECT action, actor, note, created_at FROM access_recovery_events WHERE request_id = ? ORDER BY id", id);
    }

    @Transactional
    public Map<String, Object> link(long id, Map<String, Object> payload, Authentication actor) {
        requireRecovery(actor); pending(id); verifyIdentity(payload);
        var accounts = jdbc.queryForList("SELECT id, username FROM users WHERE lower(username) = lower(?) LIMIT 2", text(payload.get("username"), 255));
        if (accounts.size() != 1) throw bad("Нужна одна существующая учётная запись с указанным логином");
        long userId = ((Number) accounts.get(0).get("id")).longValue();
        protectAdministrator(userId, actor);
        jdbc.update("UPDATE password_reset_requests SET user_id = ?, status = 'PENDING', lookup_result = 'LINKED' WHERE id = ?", userId, id);
        audit(id, "IDENTITY_VERIFIED", actor.getName(), text(payload.get("note"), 1000));
        audit(id, "LINKED", actor.getName(), "УЗ: " + accounts.get(0).get("username"));
        invalidateAfterCommit();
        return Map.of("success", true);
    }

    @Transactional
    public Map<String, Object> approve(long id, Map<String, Object> payload, Authentication actor) {
        requireRecovery(actor);
        Map<String, Object> row = pending(id);
        verifyIdentity(payload);
        if (!(row.get("user_id") instanceof Number user)) throw bad("Сначала определите и привяжите существующую учётную запись");
        long userId = user.longValue();
        var accounts = jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", userId);
        if (accounts.isEmpty()) throw bad("Учётная запись больше не существует");
        protectAdministrator(userId, actor);
        boolean reset = Boolean.TRUE.equals(payload.get("reset_password"));
        boolean unblock = Boolean.TRUE.equals(payload.get("unblock_account"));
        if (!reset && !unblock) throw bad("Выберите сброс пароля, снятие блокировки или оба действия");
        String temporary = reset ? temporaryPassword() : "";
        String hash = reset ? passwords.encode(temporary) : "";
        if (reset) {
            Set<String> columns = com.example.panel.support.JdbcSchemaInspector.loadColumnNames(jdbc, "users");
            if (columns.contains("password_hash")) jdbc.update("UPDATE users SET password = ?, password_hash = ? WHERE id = ?", hash, hash, userId);
            else jdbc.update("UPDATE users SET password = ? WHERE id = ?", hash, userId);
        }
        if (unblock) jdbc.update("UPDATE users SET is_blocked = FALSE, enabled = TRUE WHERE id = ?", userId);
        jdbc.update("UPDATE users SET session_version = session_version + 1 WHERE id = ?", userId);
        String note = text(payload.get("note"), 1000);
        audit(id, "IDENTITY_VERIFIED", actor.getName(), note);
        if (reset) audit(id, "PASSWORD_RESET", actor.getName(), null);
        if (unblock) audit(id, "ACCOUNT_UNBLOCKED", actor.getName(), null);
        resolve(id, "APPROVED", note, actor.getName());
        jdbc.update("""
                UPDATE password_reset_requests SET status = 'SUPERSEDED', resolved_at = CURRENT_TIMESTAMP,
                    resolved_by_username = ?, resolution_note = ?
                WHERE user_id = ? AND id <> ? AND status IN ('PENDING','NEEDS_REVIEW')
                """, actor.getName(), "Обработано в заявке №" + id, userId, id);
        invalidateAfterCommit();
        return new IssuedCredential(temporary,
                reset ? "Передайте временный пароль пользователю после проверки личности." : "Блокировка учётной записи снята.");
    }

    @Transactional
    public Map<String, Object> reject(long id, Map<String, Object> payload, Authentication actor) {
        requireRecovery(actor); pending(id);
        String note = text(payload.get("note"), 1000);
        if (note.isBlank()) throw bad("Укажите причину отклонения");
        resolve(id, "REJECTED", note, actor.getName());
        audit(id, "REJECTED", actor.getName(), note);
        invalidateAfterCommit();
        return Map.of("success", true);
    }

    private Map<String, Object> pending(long id) {
        // Rare operator decisions share an ordering lock, including superseding other open requests.
        jdbc.execute("SELECT pg_advisory_xact_lock(104, 1)");
        var rows = jdbc.queryForList("SELECT id, user_id, status FROM password_reset_requests WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена");
        Map<String, Object> row = rows.get(0);
        if (!Set.of("PENDING", "NEEDS_REVIEW").contains(row.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже обработана; обновите список");
        return row;
    }
    private void protectAdministrator(long id, Authentication actor) {
        Boolean privileged = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ?
                    AND (lower(u.username) = 'admin' OR lower(r.name) IN ('admin','portal_admin','portal admin','администратор портала')
                    OR EXISTS(SELECT 1 FROM user_authorities a WHERE a.user_id = u.id AND a.authority IN ('ROLE_ADMIN','ROLE_PORTAL_ADMIN'))))
                """, Boolean.class, id);
        boolean administrator = "admin".equalsIgnoreCase(actor.getName()) || actor.getAuthorities().stream()
                .anyMatch(right -> Set.of("ROLE_ADMIN", "ROLE_PORTAL_ADMIN").contains(right.getAuthority()));
        if (Boolean.TRUE.equals(privileged) && !administrator)
            throw new AccessDeniedException("Доступ администратора восстанавливает другой администратор");
    }
    private void verifyIdentity(Map<String, Object> payload) {
        if (!Boolean.TRUE.equals(payload.get("identity_verified")) || text(payload.get("note"), 1000).isBlank())
            throw bad("Подтвердите проверку личности и укажите основание решения. Контакт из заявки сам по себе не подтверждает личность.");
    }
    private void resolve(long id, String status, String note, String actor) {
        jdbc.update("UPDATE password_reset_requests SET status = ?, resolution_note = ?, resolved_at = CURRENT_TIMESTAMP, resolved_by_username = ? WHERE id = ?", status, note, actor, id);
    }
    private void audit(Long id, String action, String actor, String note) {
        jdbc.update("INSERT INTO access_recovery_events(request_id, action, actor, note) VALUES (?, ?, ?, ?)", id, action, actor, note);
    }
    private void invalidateAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { events.publishAttentionChanged(); }
                catch (RuntimeException unavailable) {
                    org.slf4j.LoggerFactory.getLogger(AccessRecoveryService.class).warn("Attention refresh unavailable; queue remains durable");
                }
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.access-recovery.cleanup-interval:3600000}")
    @Transactional
    public void scrubExpiredMetadata() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(Math.max(1, properties.getMetadataRetentionDays()));
        jdbc.update("""
                UPDATE password_reset_requests SET requested_by_ip = NULL, connection_ip = NULL,
                    requested_user_agent = NULL, correlation_id = NULL
                WHERE last_requested_at < ? AND (requested_by_ip IS NOT NULL OR requested_user_agent IS NOT NULL OR correlation_id IS NOT NULL)
                """, cutoff);
        jdbc.update("""
                UPDATE password_reset_requests SET requester_name = NULL, requester_contact = NULL, requested_note = NULL, resolution_note = NULL
                WHERE resolved_at < ? AND status NOT IN ('PENDING','NEEDS_REVIEW')
                    AND (requester_name IS NOT NULL OR requester_contact IS NOT NULL OR requested_note IS NOT NULL OR resolution_note IS NOT NULL)
                """, cutoff);
        jdbc.update("UPDATE access_recovery_events SET note = NULL WHERE created_at < ? AND note IS NOT NULL", cutoff);
        jdbc.update("DELETE FROM public_auth_events WHERE created_at < ?", cutoff);
        jdbc.update("""
                DELETE FROM attention_request_seen s WHERE seen_at < ? AND
                    ((kind = 'recovery' AND NOT EXISTS(SELECT 1 FROM password_reset_requests r WHERE r.id=s.request_id AND r.status IN ('PENDING','NEEDS_REVIEW')))
                    OR (kind = 'unblock' AND NOT EXISTS(SELECT 1 FROM client_unblock_requests r WHERE r.id=s.request_id AND r.status='pending')))
                """, cutoff);
        jdbc.update("DELETE FROM public_auth_rate_limits WHERE window_start < ?", Instant.now().getEpochSecond() - Math.max(120, properties.getRateWindow().toSeconds() * 2));
    }
    public static String text(Object value, int max) {
        String text = value == null ? "" : value.toString().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        return text.substring(0, Math.min(max, text.length()));
    }
    private static String temporaryPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
        StringBuilder result = new StringBuilder(14);
        for (int i = 0; i < 14; i++) result.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return result.toString();
    }
    /** Spring MVC may log response.toString() at DEBUG; serialization still supplies the credential to the operator. */
    private static final class IssuedCredential extends LinkedHashMap<String, Object> {
        IssuedCredential(String password, String message) {
            put("success", true); put("temporary_password", password); put("message", message);
        }
        @Override public String toString() { return "AccessRecoveryResult{success=true, temporary_password=[REDACTED]}"; }
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
