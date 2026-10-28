package com.example.panel.service;

import com.example.panel.repository.NotificationBusinessAccessSql;
import com.example.panel.security.PanelUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Full-owner immutable BUSINESS lifecycle/category audit. No legacy child reads. */
@Service
public class BusinessTicketWorkReadService {
    private static final String SQL = """
        WITH authorized_owner AS (
            SELECT owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id,owner.business_work_revision
              FROM tickets owner
              JOIN users reader ON reader.id=:readerId AND reader.username=:readerName
              CROSS JOIN (VALUES(CAST(:identity AS TEXT))) n(user_identity)
             WHERE owner.ticket_public_id=:publicId AND owner.is_general=FALSE AND owner.business_id IS NOT NULL
               AND reader.enabled=TRUE AND reader.is_blocked=FALSE
               AND 1=(SELECT count(*) FROM tickets same_public WHERE same_public.ticket_public_id=owner.ticket_public_id)
               AND (
        """ + NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS + """
               )
        ), scoped_events AS (
            SELECT entry.revision,entry.action,entry.from_status,entry.to_status,entry.previous_categories,entry.categories,
                   entry.actor_user_id,entry.actor,entry.created_at
              FROM authorized_owner owner JOIN business_ticket_work_events entry ON
                   ROW(entry.ticket_user_id,entry.ticket_id,entry.ticket_public_id,entry.business_id)
                     = ROW(owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id)
        )
        SELECT owner.*,COALESCE((SELECT max(revision) FROM scoped_events),0) AS latest_event_revision,
               event.revision AS event_revision,event.action,event.from_status,event.to_status,
               event.previous_categories,event.categories,event.actor_user_id,event.actor,event.created_at
          FROM authorized_owner owner LEFT JOIN LATERAL (
              SELECT * FROM scoped_events WHERE CAST(:before AS BIGINT) IS NULL OR revision<:before
               ORDER BY revision DESC LIMIT :pageSize
          ) event ON TRUE ORDER BY event.revision DESC
        """;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    public BusinessTicketWorkReadService(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.json=json; }

    public Optional<History> history(String publicId,Authentication authentication,Long before,int limit) {
        if(limit<1 || limit>100 || before!=null && before<1) throw new IllegalArgumentException("Invalid work history page");
        if(publicId==null || !publicId.matches("[a-f0-9]{32}") || authentication==null
            || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
            || !(authentication.getPrincipal() instanceof PanelUserPrincipal account)
            || account.getUserId()<=0 || account.getUsername().isBlank()
            || !account.isEnabled() || !account.isAccountNonLocked()
            || !account.isAccountNonExpired() || !account.isCredentialsNonExpired()) return Optional.empty();
        try {
            return jdbc.query(SQL,new MapSqlParameterSource("publicId",publicId)
                .addValue("readerId",account.getUserId()).addValue("readerName",account.getUsername())
                .addValue("identity",account.getUsername().toLowerCase(Locale.ROOT)).addValue("before",before,Types.BIGINT)
                .addValue("pageSize",limit+1),rs->{
                    Owner owner=null;long revision=0,latest=0;
                    var events=new ArrayList<Event>();
                    while(rs.next()) {
                        if(owner==null) {
                            owner=new Owner(rs.getString("user_id"),rs.getString("ticket_id"),rs.getString("ticket_public_id"),rs.getString("business_id"));
                            revision=rs.getLong("business_work_revision");latest=rs.getLong("latest_event_revision");
                        }
                        if(rs.getObject("event_revision")!=null) {
                            events.add(new Event(rs.getLong("event_revision"),rs.getString("action"),rs.getString("from_status"),rs.getString("to_status"),
                                categories(rs.getString("previous_categories")),categories(rs.getString("categories")),rs.getLong("actor_user_id"),
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
    private List<String> categories(String value) {
        try {
            var node=json.readTree(value);
            if(node==null || !node.isArray()) throw new IllegalArgumentException("Invalid category snapshot");
            var result=new ArrayList<String>();
            for(var item:node) {
                if(!item.isTextual()) throw new IllegalArgumentException("Invalid category snapshot");
                result.add(item.textValue());
            }
            return List.copyOf(result);
        } catch(JsonProcessingException|IllegalArgumentException ex) { throw new ReadUnavailableException(ex); }
    }
    public record Owner(String userId,String ticketId,String ticketPublicId,String businessId) { }
    public record Event(@JsonSerialize(using=ToStringSerializer.class) long revision,String action,String fromStatus,String toStatus,
                        List<String> previousCategories,List<String> categories,@JsonSerialize(using=ToStringSerializer.class) long actorId,
                        String actor,OffsetDateTime createdAt) {
        public Event { previousCategories=List.copyOf(previousCategories);categories=List.copyOf(categories); }
    }
    public record History(Owner owner,@JsonSerialize(using=ToStringSerializer.class) long revision,
                          @JsonSerialize(using=ToStringSerializer.class) long latestEventRevision,List<Event> events,
                          boolean hasMore,@JsonSerialize(using=ToStringSerializer.class) Long nextBeforeRevision) {
        public History { events=List.copyOf(events); }
    }
    public static class ReadUnavailableException extends RuntimeException {
        public ReadUnavailableException(Throwable cause) { super("Work history is unavailable",cause); }
    }
}
