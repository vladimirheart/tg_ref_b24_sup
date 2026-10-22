package com.example.panel.service;

import com.example.panel.repository.NotificationBusinessAccessSql;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Historical assignments only: audit snapshots do not establish the current responsible. */
@Service
public class BusinessTicketAssignmentReadService {
    private static final String TUPLE = """
        ROW(entry.ticket_user_id,entry.ticket_id,entry.ticket_public_id,entry.business_id)
          = ROW(owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id)
        """;
    private static final String SQL = """
        WITH authorized_owner AS (
            SELECT owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id,owner.responsibility_revision
              FROM tickets owner CROSS JOIN (VALUES(CAST(:identity AS TEXT))) n(user_identity)
             WHERE owner.ticket_public_id=:publicId AND owner.is_general=FALSE AND owner.business_id IS NOT NULL
               AND 1=(SELECT count(*) FROM tickets same_public WHERE same_public.ticket_public_id=owner.ticket_public_id)
               AND (
        """ + NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS + """
               )
        ), scoped_events AS (
            SELECT entry.revision,'MANUAL' AS origin,entry.action,entry.assignee_user_id,entry.assignee,
                   entry.actor_user_id,entry.actor,entry.created_at
              FROM authorized_owner owner JOIN business_ticket_assignment_events entry ON
        """ + TUPLE + """
            UNION ALL
            SELECT entry.revision,'SLA' AS origin,'assign' AS action,CAST(NULL AS BIGINT),entry.assignee,
                   CAST(NULL AS BIGINT),entry.actor,entry.created_at
              FROM authorized_owner owner JOIN scoped_sla_assignment_events entry ON
        """ + TUPLE + """
               AND entry.scope_kind='BUSINESS'
        ), counted_events AS (
            SELECT *,count(*) OVER(PARTITION BY revision) AS same_revision FROM scoped_events
        )
        SELECT owner.*,COALESCE((SELECT max(revision) FROM scoped_events),0) AS latest_event_revision,
               event.revision AS event_revision,event.origin,event.action,event.assignee_user_id,event.assignee,
               event.actor_user_id,event.actor,event.created_at,event.same_revision
          FROM authorized_owner owner LEFT JOIN LATERAL (
              SELECT * FROM counted_events WHERE CAST(:before AS BIGINT) IS NULL OR revision<:before
               ORDER BY revision DESC LIMIT :pageSize
          ) event ON TRUE ORDER BY event.revision DESC
        """;
    private final NamedParameterJdbcTemplate jdbc;
    public BusinessTicketAssignmentReadService(JdbcTemplate jdbc) { this.jdbc=new NamedParameterJdbcTemplate(jdbc); }

    public Optional<History> history(String publicId,String identity,Long before,int limit) {
        if(limit<1 || limit>100 || before!=null && before<1) throw new IllegalArgumentException("Invalid assignment history page");
        if(publicId==null || !publicId.matches("[a-f0-9]{32}") || identity==null || identity.isBlank()) return Optional.empty();
        try {
            return jdbc.query(SQL,new MapSqlParameterSource("publicId",publicId)
                .addValue("identity",identity.toLowerCase(Locale.ROOT)).addValue("before",before,Types.BIGINT)
                .addValue("pageSize",limit+1),rs->{
                    Owner owner=null;long revision=0,latest=0;
                    var events=new ArrayList<Event>();
                    while(rs.next()) {
                        if(owner==null) {
                            owner=new Owner(rs.getString("user_id"),rs.getString("ticket_id"),rs.getString("ticket_public_id"),rs.getString("business_id"));
                            revision=rs.getLong("responsibility_revision");latest=rs.getLong("latest_event_revision");
                        }
                        if(rs.getObject("event_revision")!=null) {
                            if(rs.getLong("same_revision")!=1) throw new ReadUnavailableException(new IllegalStateException("Ambiguous assignment revision"));
                            events.add(new Event(rs.getLong("event_revision"),rs.getString("origin"),rs.getString("action"),
                                rs.getObject("assignee_user_id",Long.class),rs.getString("assignee"),rs.getObject("actor_user_id",Long.class),
                                rs.getString("actor"),rs.getObject("created_at",OffsetDateTime.class)));
                        }
                    }
                    if(owner==null) return Optional.<History>empty();
                    boolean hasMore=events.size()>limit;
                    if(hasMore) events.remove(events.size()-1);
                    return Optional.of(new History(owner,revision,latest,events,hasMore,hasMore?events.get(events.size()-1).revision():null));
                });
        } catch(DataAccessException ex) { throw new ReadUnavailableException(ex); }
    }
    public record Owner(String userId,String ticketId,String ticketPublicId,String businessId) { }
    public record Event(@JsonSerialize(using=ToStringSerializer.class) long revision,String origin,String action,
                        @JsonSerialize(using=ToStringSerializer.class) Long assigneeId,String assignee,
                        @JsonSerialize(using=ToStringSerializer.class) Long actorId,String actor,OffsetDateTime createdAt) { }
    public record History(Owner owner,@JsonSerialize(using=ToStringSerializer.class) long revision,
                          @JsonSerialize(using=ToStringSerializer.class) long latestEventRevision,List<Event> events,
                          boolean hasMore,@JsonSerialize(using=ToStringSerializer.class) Long nextBeforeRevision) {
        public History { events=List.copyOf(events); }
    }
    public static class ReadUnavailableException extends RuntimeException {
        public ReadUnavailableException(Throwable cause) { super("Assignment history is unavailable",cause); }
    }
}
