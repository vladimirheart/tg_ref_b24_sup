package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Observed manual GENERAL intervals only; legacy work totals have no proven scope. */
@Service
public class GeneralTicketWorkTimeService {
    private static final String OWNER="""
        ticket.user_id=:ownerId AND ticket.ticket_id=:ticketId AND ticket.ticket_public_id=:publicId
        AND ticket.is_general=TRUE AND ticket.business_id IS NULL
        AND 1=(SELECT COUNT(*) FROM tickets collision WHERE collision.ticket_id=ticket.ticket_id)
        """;
    private static final String JOIN="""
        FROM tickets ticket CROSS JOIN users account
        LEFT JOIN general_ticket_work_intervals span ON span.ticket_user_id=ticket.user_id
            AND span.ticket_id=ticket.ticket_id AND span.ticket_public_id=ticket.ticket_public_id
        WHERE lower(account.username)=lower(:identity) AND
        """+OWNER+" AND "+GeneralTicketAccessService.READ_ACCOUNT;
    private static final String ACTIVE="span.outcome='OPEN' AND ticket.status='open' "
        +"AND span.opened_revision=ticket.general_work_revision AND span.started_at<=CURRENT_TIMESTAMP";
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;

    public GeneralTicketWorkTimeService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags) {
        this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;
    }
    public Optional<Summary> summary(GeneralTicketEventScope scope,String identity) {
        if (!allowed(scope,identity)) return Optional.empty();
        try { return jdbc.query("""
            SELECT COALESCE(FLOOR(SUM(EXTRACT(EPOCH FROM (span.ended_at-span.started_at)))
                    FILTER(WHERE span.outcome='COMPLETED')),0)::bigint AS completed_seconds,
                COUNT(*) FILTER(WHERE span.outcome='COMPLETED') AS completed_intervals,
                COUNT(*) FILTER(WHERE span.outcome='INVALIDATED' OR (span.outcome='OPEN' AND NOT (
            """+ACTIVE+"""
                ))) AS invalid_intervals,
                MIN(span.started_at) AS observed_since,
                MAX(span.started_at) FILTER(WHERE
            """+ACTIVE+") AS active_started_at "+JOIN+"""
            GROUP BY ticket.user_id,ticket.ticket_id,ticket.ticket_public_id,ticket.status,ticket.general_work_revision
            """,params(scope,identity),(rs,row)->new Summary(rs.getLong("completed_seconds"),rs.getLong("completed_intervals"),
                rs.getLong("invalid_intervals"),rs.getObject("observed_since",OffsetDateTime.class),
                rs.getObject("active_started_at",OffsetDateTime.class))).stream().findFirst();
        } catch (DataAccessException ex) { return Optional.empty(); }
    }
    public List<Interval> recent(GeneralTicketEventScope scope,String identity) {
        if (!allowed(scope,identity)) return List.of();
        try { return jdbc.query("""
            SELECT span.started_at,span.ended_at,span.started_by,span.ended_by,
                CASE WHEN span.outcome='OPEN' AND NOT (
            """+ACTIVE+"""
                ) THEN 'UNCONFIRMED' ELSE span.outcome END AS display_outcome,
                CASE WHEN span.outcome='COMPLETED' THEN FLOOR(EXTRACT(EPOCH FROM (span.ended_at-span.started_at)))::bigint
                    ELSE 0 END AS completed_seconds
            """+JOIN+" AND span.id IS NOT NULL ORDER BY span.id DESC LIMIT 10",params(scope,identity),
            (rs,row)->new Interval(rs.getObject("started_at",OffsetDateTime.class),rs.getObject("ended_at",OffsetDateTime.class),
                rs.getString("started_by"),rs.getString("ended_by"),rs.getString("display_outcome"),rs.getLong("completed_seconds")));
        } catch (DataAccessException ex) { return List.of(); }
    }
    private boolean allowed(GeneralTicketEventScope scope,String identity) {
        return flags.isFoundationEnabled()&&flags.isGeneralTicketQueue()&&scope!=null&&scope.isValid()&&identity!=null&&!identity.isBlank();
    }
    private MapSqlParameterSource params(GeneralTicketEventScope scope,String identity) {
        return new MapSqlParameterSource("ownerId",scope.ticketUserId()).addValue("ticketId",scope.ticketId())
            .addValue("publicId",scope.ticketPublicId()).addValue("identity",identity);
    }
    public record Summary(long completedSeconds,long completedIntervals,long invalidIntervals,
            OffsetDateTime observedSince,OffsetDateTime activeStartedAt) { }
    public record Interval(OffsetDateTime startedAt,OffsetDateTime endedAt,String startedBy,String endedBy,
            String outcome,long completedSeconds) { }
}
