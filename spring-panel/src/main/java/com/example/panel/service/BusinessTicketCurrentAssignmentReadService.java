package com.example.panel.service;

import com.example.panel.repository.NotificationBusinessAccessSql;
import com.example.panel.security.PanelUserPrincipal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Current state requires both the live row and matching full-owner revision provenance. */
@Service
public class BusinessTicketCurrentAssignmentReadService {
    private static final String TUPLE = """
        ROW(entry.ticket_user_id,entry.ticket_id,entry.ticket_public_id,entry.business_id)
          = ROW(owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id)
        """;
    private static final String SQL = """
        WITH authorized_owner AS (
            SELECT owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id,
                   owner.responsibility_revision
              FROM tickets owner
              JOIN users reader ON reader.id=:readerId AND reader.username=:username
              CROSS JOIN (VALUES(CAST(:identity AS TEXT))) n(user_identity)
             WHERE owner.ticket_public_id=:publicId AND owner.is_general=FALSE AND owner.business_id IS NOT NULL
               AND reader.enabled=TRUE AND reader.is_blocked=FALSE
               AND 1=(SELECT count(*) FROM tickets same_public WHERE same_public.ticket_public_id=owner.ticket_public_id)
               AND (
        """ + NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS + """
               )
        ), scoped_events AS (
            SELECT entry.revision,'MANUAL' AS origin,entry.assignee_user_id,entry.assignee,entry.created_at
              FROM authorized_owner owner JOIN business_ticket_assignment_events entry ON
        """ + TUPLE + """
            UNION ALL
            SELECT entry.revision,'SLA' AS origin,CAST(NULL AS BIGINT),entry.assignee,entry.created_at
              FROM authorized_owner owner JOIN scoped_sla_assignment_events entry ON
        """ + TUPLE + """
               AND entry.scope_kind='BUSINESS'
        )
        SELECT owner.*,
               (SELECT count(*) FROM tickets same_raw WHERE same_raw.ticket_id=owner.ticket_id) AS raw_roots,
               (SELECT count(*) FROM ticket_responsibles live WHERE live.ticket_id=owner.ticket_id) AS live_rows,
               (SELECT min(live.responsible) FROM ticket_responsibles live WHERE live.ticket_id=owner.ticket_id) AS live_assignee,
               (SELECT count(*) FROM scoped_events) AS event_total,
               (SELECT count(*) FROM scoped_events WHERE revision=owner.responsibility_revision) AS current_events,
               COALESCE((SELECT max(revision) FROM scoped_events),0) AS latest_revision,
               event.origin,event.assignee_user_id,event.assignee,event.created_at
          FROM authorized_owner owner LEFT JOIN LATERAL (
              SELECT * FROM scoped_events WHERE revision=owner.responsibility_revision LIMIT 1
          ) event ON TRUE
        """;
    private final NamedParameterJdbcTemplate jdbc;
    public BusinessTicketCurrentAssignmentReadService(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public Optional<State> current(String publicId, Authentication authentication) {
        if (publicId == null || !publicId.matches("[a-f0-9]{32}") || authentication == null
                || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof PanelUserPrincipal account)
                || account.getUserId() <= 0 || account.getUsername().isBlank()
                || !account.isEnabled() || !account.isAccountNonLocked()
                || !account.isAccountNonExpired() || !account.isCredentialsNonExpired()) return Optional.empty();
        var parameters = new MapSqlParameterSource("publicId", publicId).addValue("readerId", account.getUserId())
                .addValue("username", account.getUsername()).addValue("identity", account.getUsername().toLowerCase(Locale.ROOT));
        try {
            var states = jdbc.query(SQL, parameters, (rs, row) -> {
                var owner = new Owner(rs.getString("user_id"), rs.getString("ticket_id"),
                        rs.getString("ticket_public_id"), rs.getString("business_id"));
                long revision = rs.getLong("responsibility_revision");
                String status = "UNVERIFIED";
                Assignee assignee = null;
                if (rs.getLong("raw_roots") == 1 && revision == 0 && rs.getLong("live_rows") == 0
                        && rs.getLong("event_total") == 0) {
                    status = "UNASSIGNED";
                } else if (rs.getLong("raw_roots") == 1 && revision > 0 && rs.getLong("live_rows") == 1
                        && rs.getLong("current_events") == 1 && rs.getLong("latest_revision") == revision) {
                    String snapshot = rs.getString("assignee");
                    String origin = rs.getString("origin");
                    Long accountId = rs.getObject("assignee_user_id", Long.class);
                    if (snapshot != null && !snapshot.isBlank() && Objects.equals(snapshot, rs.getString("live_assignee"))
                            && ("SLA".equals(origin) && accountId == null
                            || "MANUAL".equals(origin) && accountId != null && accountId > 0)) {
                        status = "ASSIGNED";
                        assignee = new Assignee(accountId, snapshot, origin, rs.getObject("created_at", OffsetDateTime.class));
                    }
                }
                return new State(owner, revision, status, assignee);
            });
            return states.size() == 1 ? Optional.of(states.get(0)) : Optional.empty();
        } catch (DataAccessException ex) {
            throw new ReadUnavailableException(ex);
        }
    }

    public record Owner(String userId, String ticketId, String ticketPublicId, String businessId) { }
    public record Assignee(@JsonSerialize(using=ToStringSerializer.class) Long id, String username,
                           String origin, OffsetDateTime assignedAt) { }
    public record State(Owner owner, @JsonSerialize(using=ToStringSerializer.class) long revision,
                        String status, Assignee assignee) { }
    public static class ReadUnavailableException extends RuntimeException {
        public ReadUnavailableException(Throwable cause) { super("Current assignment is unavailable", cause); }
    }
}
