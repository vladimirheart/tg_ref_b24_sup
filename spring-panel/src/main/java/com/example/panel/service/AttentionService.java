package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttentionService {
    private final JdbcTemplate jdbc;
    public AttentionService(@Qualifier("usersJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> summary(Authentication actor) {
        long userId = userId(actor);
        Map<String, Object> unblocks = counts(userId, "unblock", "client_unblock_requests", "status = 'pending'");
        if (!AccessRecoveryService.canRecover(actor)) return Map.of("unblock", unblocks);
        return Map.of("unblock", unblocks, "recovery", counts(userId, "recovery", "password_reset_requests", "status IN ('PENDING','NEEDS_REVIEW')"));
    }

    private Map<String, Object> counts(long userId, String kind, String table, String pending) {
        return jdbc.queryForMap("SELECT count(*) AS pending, count(*) FILTER (WHERE s.request_id IS NULL) AS unseen FROM "
                + table + " r LEFT JOIN attention_request_seen s ON s.request_id = r.id AND s.user_id = ? AND s.kind = ? WHERE r." + pending, userId, kind);
    }

    @Transactional
    public void seen(Authentication actor, String kind, List<Long> ids) {
        long userId = userId(actor);
        if (kind == null || !List.of("unblock", "recovery").contains(kind)) throw new IllegalArgumentException("Неизвестный тип заявки");
        if ("recovery".equals(kind) && !AccessRecoveryService.canRecover(actor)) throw new AccessDeniedException("Нет права «Восстановить доступ»");
        if (ids == null || ids.size() > 300) throw new IllegalArgumentException("Слишком много заявок");
        String table = "unblock".equals(kind) ? "client_unblock_requests" : "password_reset_requests";
        for (Long id : ids.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            jdbc.update("INSERT INTO attention_request_seen(user_id, kind, request_id) SELECT ?, ?, id FROM "
                    + table + " WHERE id = ? ON CONFLICT DO NOTHING", userId, kind, id);
        }
    }

    private long userId(Authentication actor) {
        if (actor == null || !actor.isAuthenticated() || !(actor.getPrincipal() instanceof PanelUserPrincipal principal))
            throw new AccessDeniedException("Нужна действующая учётная запись панели");
        return principal.getUserId();
    }
}
