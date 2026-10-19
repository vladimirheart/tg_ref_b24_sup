package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import java.sql.Types;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Manual GENERAL status changes; never enters legacy lifecycle, feedback or client delivery. */
@Service
public class GeneralTicketWorkService {
    private static final String OWNER="""
        ticket.is_general=TRUE AND ticket.business_id IS NULL
        AND 1=(SELECT COUNT(*) FROM tickets collision WHERE collision.ticket_id=ticket.ticket_id)
        """;
    private final NamedParameterJdbcTemplate jdbc;
    private final UiEventOutboxAppendService ids;
    private final MultibusinessFeatureFlags flags;
    public GeneralTicketWorkService(JdbcTemplate jdbc,UiEventOutboxAppendService ids,MultibusinessFeatureFlags flags) {
        this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.ids=ids;this.flags=flags;
    }
    public Optional<View> find(String publicId,String identity) {
        if (!enabled() || publicId==null || !publicId.matches("[a-f0-9]{32}") || identity==null || identity.isBlank()) return Optional.empty();
        String sql="SELECT ticket.*,EXISTS(SELECT 1 FROM general_ticket_operate_grants operation WHERE operation.user_id=account.id AND operation.is_active=TRUE) AS can_operate "
            + "FROM tickets ticket CROSS JOIN users account WHERE ticket.ticket_public_id=:publicId AND lower(account.username)=lower(:identity) AND "
            + OWNER+" AND "+GeneralTicketAccessService.READ_ACCOUNT;
        try { return jdbc.query(sql,new MapSqlParameterSource("publicId",publicId).addValue("identity",identity),
            (rs,row)->new View(new GeneralTicketEventScope(rs.getLong("user_id"),rs.getString("ticket_id"),rs.getString("ticket_public_id")),
                rs.getString("status"),rs.getLong("general_work_revision"),rs.getBoolean("can_operate"))).stream().findFirst(); }
        catch (DataAccessException ex) { return Optional.empty(); }
    }
    public boolean transition(GeneralTicketEventScope scope,String identity,long revision,String expectedStatus,String nextStatus) {
        if (!enabled() || scope==null || !scope.isValid() || identity==null || identity.isBlank() || revision<0
                || !allowedTransition(expectedStatus,nextStatus)) return false;
        String sql="""
            WITH changed AS (
                UPDATE tickets ticket SET status=:nextStatus,
                    resolved_at=CASE WHEN :nextStatus='closed' THEN CURRENT_TIMESTAMP
                        WHEN :expectedStatus='closed' THEN NULL ELSE ticket.resolved_at END,
                    resolved_by=CASE WHEN :nextStatus='closed' THEN account.username
                        WHEN :expectedStatus='closed' THEN NULL ELSE ticket.resolved_by END,
                    closed_count=CASE WHEN :nextStatus='closed' THEN COALESCE(ticket.closed_count,0)+1 ELSE ticket.closed_count END,
                    reopen_count=CASE WHEN :expectedStatus='closed' THEN COALESCE(ticket.reopen_count,0)+1 ELSE ticket.reopen_count END,
                    last_reopen_at=CASE WHEN :expectedStatus='closed' THEN CURRENT_TIMESTAMP ELSE ticket.last_reopen_at END
                FROM users account
                WHERE ticket.user_id=:ownerId AND ticket.ticket_id=:ticketId AND ticket.ticket_public_id=:publicId
                  AND ticket.general_work_revision=:revision AND ticket.status=:expectedStatus
                  AND general_ticket_transition_allowed(ticket.status,:nextStatus)
                  AND lower(account.username)=lower(:identity) AND
            """+OWNER+" AND "+GeneralTicketAccessService.OPERATE_ACCOUNT+"""
                RETURNING ticket.user_id,ticket.ticket_id,ticket.ticket_public_id,ticket.channel_id,
                    ticket.general_work_revision,account.id AS actor_id,account.username AS actor,clock_timestamp() AS changed_at
            ), audit AS (
                INSERT INTO general_ticket_work_events(ticket_user_id,ticket_id,ticket_public_id,revision,from_status,to_status,actor_user_id,actor)
                SELECT user_id,ticket_id,ticket_public_id,general_work_revision,:expectedStatus,:nextStatus,actor_id,actor FROM changed
                WHERE record_general_work_interval(user_id,ticket_id,ticket_public_id,:expectedStatus,:nextStatus,
                    :revision,general_work_revision,actor_id,actor,changed_at)
                RETURNING ticket_public_id,revision
            )
            INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,created_at,
                ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id)
            SELECT :eventId,'ticket_status_changed',changed.ticket_id,changed.channel_id,CURRENT_TIMESTAMP,
                changed.user_id,changed.ticket_public_id,'GENERAL',NULL FROM changed
                JOIN audit ON audit.ticket_public_id=changed.ticket_public_id AND audit.revision=changed.general_work_revision
            """;
        var args=new MapSqlParameterSource("ownerId",scope.ticketUserId()).addValue("ticketId",scope.ticketId(),Types.VARCHAR)
            .addValue("publicId",scope.ticketPublicId(),Types.VARCHAR).addValue("identity",identity,Types.VARCHAR)
            .addValue("revision",revision,Types.BIGINT).addValue("expectedStatus",expectedStatus,Types.VARCHAR)
            .addValue("nextStatus",nextStatus,Types.VARCHAR).addValue("eventId",ids.nextEventId(),Types.BIGINT);
        try { return jdbc.update(sql,args)==1; } catch (DataAccessException ex) { return false; }
    }
    private boolean allowedTransition(String from,String to) {
        return "pending".equals(from) && ("open".equals(to) || "closed".equals(to))
            || "open".equals(from) && ("pending".equals(to) || "closed".equals(to))
            || "closed".equals(from) && "pending".equals(to);
    }
    private boolean enabled() { return flags.isFoundationEnabled()&&flags.isGeneralTicketQueue(); }
    public record View(GeneralTicketEventScope scope,String status,long revision,boolean canOperate) { }
}
