package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit GENERAL responsibility, isolated from legacy assignment and delivery. */
@Service
public class GeneralTicketAssignmentService {
    private static final String OWNER="""
        ticket.user_id=:ownerId AND ticket.ticket_id=:ticketId AND ticket.ticket_public_id=:publicId
        AND ticket.is_general=TRUE AND ticket.business_id IS NULL
        AND 1=(SELECT COUNT(*) FROM tickets collision WHERE collision.ticket_id=ticket.ticket_id)
        """;
    private static final String TARGET=GeneralTicketAccessService.OPERATE_ACCOUNT.replace("account.","candidate.")
        +" AND candidate.username IS NOT NULL AND btrim(candidate.username)<>''";
    private static final String LATEST="""
        LEFT JOIN LATERAL (SELECT assignment.* FROM general_ticket_assignment_events assignment
            WHERE assignment.ticket_user_id=ticket.user_id AND assignment.ticket_id=ticket.ticket_id
                AND assignment.ticket_public_id=ticket.ticket_public_id ORDER BY assignment.revision DESC LIMIT 1) entry ON TRUE
        """;
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final UiEventOutboxAppendService ids;
    private final TransactionTemplate transaction;

    public GeneralTicketAssignmentService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,UiEventOutboxAppendService ids) {
        this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.ids=ids;
        transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Optional<State> current(GeneralTicketEventScope scope,String identity) {
        if (!allowed(scope,identity)) return Optional.empty();
        try { return jdbc.query("""
            SELECT COALESCE(entry.revision,0) AS revision,entry.assignee_user_id,entry.assignee,
                EXISTS(SELECT 1 FROM users candidate WHERE candidate.id=entry.assignee_user_id AND
            """+TARGET+") AS eligible FROM tickets ticket CROSS JOIN users account "+LATEST
            +" WHERE lower(account.username)=lower(:identity) AND "+OWNER+" AND "+GeneralTicketAccessService.READ_ACCOUNT,
            params(scope,identity),(rs,row)->new State(rs.getLong("revision"),rs.getObject("assignee_user_id",Long.class),
                rs.getString("assignee"),rs.getBoolean("eligible"))).stream().findFirst();
        } catch (DataAccessException ex) { return Optional.empty(); }
    }
    public List<Candidate> candidates(GeneralTicketEventScope scope,String identity) {
        if (!allowed(scope,identity)) return List.of();
        try { return jdbc.query("SELECT candidate.id,candidate.username FROM tickets ticket CROSS JOIN users account CROSS JOIN users candidate "
            +"WHERE lower(account.username)=lower(:identity) AND "+OWNER+" AND "+GeneralTicketAccessService.OPERATE_ACCOUNT
            +" AND ticket.status IN ('pending','open') AND "+TARGET+" ORDER BY lower(candidate.username),candidate.id LIMIT 200",
            params(scope,identity),(rs,row)->new Candidate(rs.getLong("id"),rs.getString("username")));
        } catch (DataAccessException ex) { return List.of(); }
    }
    public List<Event> recent(GeneralTicketEventScope scope,String identity) {
        if (!allowed(scope,identity)) return List.of();
        try { return jdbc.query("""
            SELECT entry.revision,entry.assignee,entry.actor,entry.created_at FROM tickets ticket
            JOIN general_ticket_assignment_events entry ON entry.ticket_user_id=ticket.user_id AND entry.ticket_id=ticket.ticket_id
                AND entry.ticket_public_id=ticket.ticket_public_id CROSS JOIN users account
            WHERE lower(account.username)=lower(:identity) AND
            """+OWNER+" AND "+GeneralTicketAccessService.READ_ACCOUNT+" ORDER BY entry.revision DESC LIMIT 10",
            params(scope,identity),(rs,row)->new Event(rs.getLong("revision"),rs.getString("assignee"),rs.getString("actor"),
                rs.getObject("created_at",OffsetDateTime.class)));
        } catch (DataAccessException ex) { return List.of(); }
    }
    public boolean assign(GeneralTicketEventScope scope,String identity,long ownerRevision,long assignmentRevision,Long targetId) {
        if (!allowed(scope,identity)||ownerRevision<0||assignmentRevision<0||assignmentRevision==Long.MAX_VALUE||targetId!=null&&targetId<=0) return false;
        try { return Boolean.TRUE.equals(transaction.execute(tx->{
            // The root lock serializes assignment CAS with other assignments and all status/owner writers.
            var roots=jdbc.query("""
                SELECT ticket.channel_id,account.id AS actor_id,account.username AS actor FROM tickets ticket
                JOIN users account ON lower(account.username)=lower(:identity)
                JOIN general_ticket_read_grants read_access ON read_access.user_id=account.id AND read_access.is_active=TRUE
                JOIN general_ticket_operate_grants operation ON operation.user_id=account.id AND operation.is_active=TRUE
                WHERE
                """+OWNER+" AND "+GeneralTicketAccessService.CURRENT_ACCOUNT+"""
                AND ticket.general_work_revision=:ownerRevision AND ticket.status IN ('pending','open')
                FOR UPDATE OF ticket FOR SHARE OF account,read_access,operation
                """,params(scope,identity).addValue("ownerRevision",ownerRevision),
                (rs,row)->new Root(rs.getObject("channel_id",Long.class),rs.getLong("actor_id"),rs.getString("actor")));
            if(roots.size()!=1) return false;
            String targetName=null;
            if(targetId!=null) {
                var targets=jdbc.query("""
                    SELECT candidate.id,candidate.username FROM users candidate
                    JOIN general_ticket_read_grants target_read ON target_read.user_id=candidate.id AND target_read.is_active=TRUE
                    JOIN general_ticket_operate_grants target_operation ON target_operation.user_id=candidate.id AND target_operation.is_active=TRUE
                    WHERE candidate.id=:targetId AND
                    """+TARGET+" FOR SHARE OF candidate,target_read,target_operation",
                    new MapSqlParameterSource("targetId",targetId),(rs,row)->rs.getString("username"));
                if(targets.size()!=1) return false;
                targetName=targets.get(0);
            }
            // Separate assignment revision avoids interrupting a running work interval or queued reply.
            var state=jdbc.query("SELECT revision,assignee_user_id FROM general_ticket_assignment_events "
                +"WHERE ticket_user_id=:ownerId AND ticket_id=:ticketId AND ticket_public_id=:publicId ORDER BY revision DESC LIMIT 1",
                params(scope,identity),(rs,row)->new State(rs.getLong("revision"),rs.getObject("assignee_user_id",Long.class),null,false));
            long previous=state.isEmpty()?0:state.get(0).revision();
            Long previousTarget=state.isEmpty()?null:state.get(0).assigneeUserId();
            if(previous!=assignmentRevision||Objects.equals(previousTarget,targetId)||!allowed(scope,identity)) return false;
            var root=roots.get(0);
            var args=params(scope,identity).addValue("ownerRevision",ownerRevision).addValue("revision",assignmentRevision+1)
                .addValue("targetId",targetId,Types.BIGINT).addValue("targetName",targetName,Types.VARCHAR)
                .addValue("actorId",root.actorId()).addValue("actor",root.actor());
            jdbc.update("""
                INSERT INTO general_ticket_assignment_events(ticket_user_id,ticket_id,ticket_public_id,revision,owner_revision,
                    assignee_user_id,assignee,actor_user_id,actor)
                VALUES(:ownerId,:ticketId,:publicId,:revision,:ownerRevision,:targetId,:targetName,:actorId,:actor)
                """,args);
            jdbc.update("""
                INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,created_at,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id)
                VALUES(:eventId,'assignment_changed',:ticketId,:channelId,CURRENT_TIMESTAMP,:ownerId,:publicId,'GENERAL',NULL)
                """,args.addValue("eventId",ids.nextEventId()).addValue("channelId",root.channelId(),Types.BIGINT));
            return true;
        })); } catch (RuntimeException ex) { return false; }
    }
    private boolean allowed(GeneralTicketEventScope scope,String identity) {
        return flags.isFoundationEnabled()&&flags.isGeneralTicketQueue()&&scope!=null&&scope.isValid()&&identity!=null&&!identity.isBlank();
    }
    private MapSqlParameterSource params(GeneralTicketEventScope scope,String identity) {
        return new MapSqlParameterSource("ownerId",scope.ticketUserId()).addValue("ticketId",scope.ticketId())
            .addValue("publicId",scope.ticketPublicId()).addValue("identity",identity);
    }
    public record State(long revision,Long assigneeUserId,String assignee,boolean eligible) { }
    public record Candidate(long userId,String username) { }
    public record Event(long revision,String assignee,String actor,OffsetDateTime createdAt) { }
    private record Root(Long channelId,long actorId,String actor) { }
}
