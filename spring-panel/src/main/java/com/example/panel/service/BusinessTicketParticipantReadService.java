package com.example.panel.service;

import com.example.panel.repository.NotificationBusinessAccessSql;
import com.example.panel.security.PanelUserPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.sql.ResultSet;
import java.sql.SQLException;
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

/** Full-owner roster reads. A legacy raw ID or participation never grants access. */
@Service
public class BusinessTicketParticipantReadService {
    public static final int MAX_MEMBERS = 200;
    private static final String TUPLE = """
        ROW(entry.ticket_user_id,entry.ticket_id,entry.ticket_public_id,entry.business_id)
          = ROW(owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id)
        """;
    private static final String ROOT = """
        WITH authorized_owner AS (
            SELECT owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id,
                   (SELECT COALESCE(max(entry.revision),0) FROM business_ticket_participant_events entry
                     WHERE
        """ + TUPLE + """
                   ) AS roster_revision
              FROM tickets owner
              JOIN users reader ON reader.id=:readerId AND reader.username=:readerName
              CROSS JOIN (VALUES(CAST(:identity AS TEXT))) n(user_identity)
             WHERE owner.ticket_public_id=:publicId AND owner.is_general=FALSE
               AND owner.business_id IS NOT NULL AND reader.enabled=TRUE AND reader.is_blocked=FALSE
               AND 1=(SELECT count(*) FROM tickets same_public WHERE same_public.ticket_public_id=owner.ticket_public_id)
               AND (
        """ + NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS + ") ) ";
    private static final String MEMBER_READ = "lower(btrim(candidate.username))<>'ai_agent' AND ("
        + NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS.replace("n.user_identity", "lower(candidate.username)") + ")";
    private final NamedParameterJdbcTemplate jdbc;

    public BusinessTicketParticipantReadService(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public Optional<Roster> find(String publicId, Authentication authentication) {
        var reader=boundReader(authentication);
        if (!valid(publicId, reader)) return Optional.empty();
        try {
            return jdbc.query(ROOT + """
                SELECT owner.*,member.member_user_id,member.member AS snapshot,member.created_at AS added_at,
                       candidate.username,(
                """ + MEMBER_READ + """
                       ) AS eligible
                  FROM authorized_owner owner
                  LEFT JOIN LATERAL (
                      SELECT entry.* FROM business_ticket_participants entry WHERE
                """ + TUPLE + """
                       ORDER BY entry.revision,entry.member_user_id LIMIT :memberLimit
                  ) member ON TRUE
                  LEFT JOIN users candidate ON candidate.id=member.member_user_id
                 ORDER BY member.revision,member.member_user_id
                """, args(publicId, reader).addValue("memberLimit", MAX_MEMBERS + 1), rs -> {
                    Owner owner = null;
                    long revision = 0;
                    var members = new ArrayList<Member>();
                    while (rs.next()) {
                        if (owner == null) { owner = owner(rs); revision = rs.getLong("roster_revision"); }
                        if (rs.getObject("member_user_id") != null) {
                            members.add(new Member(rs.getLong("member_user_id"),rs.getString("snapshot"),
                                rs.getString("username"),rs.getBoolean("eligible"),rs.getObject("added_at",OffsetDateTime.class)));
                        }
                    }
                    if (owner == null) return Optional.<Roster>empty();
                    if (members.size() > MAX_MEMBERS) throw new RosterTooLargeException();
                    return Optional.of(new Roster(owner,revision,members));
                });
        } catch (DataAccessException ex) { throw new ReadUnavailableException(ex); }
    }

    public Optional<History> history(String publicId, Authentication authentication, Long before, int limit) {
        if (limit < 1 || limit > 100 || before != null && before < 1) throw new IllegalArgumentException("Invalid participant history page");
        var reader=boundReader(authentication);
        if (!valid(publicId, reader)) return Optional.empty();
        try {
            return jdbc.query(ROOT + """
                SELECT owner.*,event.revision AS event_revision,event.action,event.member_user_id,
                       event.member,event.actor_user_id,event.actor,event.created_at
                  FROM authorized_owner owner
                  LEFT JOIN LATERAL (
                      SELECT entry.* FROM business_ticket_participant_events entry WHERE
                """ + TUPLE + """
                       AND (CAST(:before AS BIGINT) IS NULL OR entry.revision<:before)
                       ORDER BY entry.revision DESC LIMIT :pageSize
                  ) event ON TRUE
                 ORDER BY event.revision DESC
                """, args(publicId,reader).addValue("before",before,Types.BIGINT).addValue("pageSize",limit+1), rs -> {
                    Owner owner = null;
                    long revision = 0;
                    var events = new ArrayList<Event>();
                    while (rs.next()) {
                        if (owner == null) { owner = owner(rs); revision = rs.getLong("roster_revision"); }
                        if (rs.getObject("event_revision") != null) {
                            events.add(new Event(rs.getLong("event_revision"),rs.getString("action"),rs.getLong("member_user_id"),
                                rs.getString("member"),rs.getLong("actor_user_id"),rs.getString("actor"),rs.getObject("created_at",OffsetDateTime.class)));
                        }
                    }
                    if (owner == null) return Optional.<History>empty();
                    boolean hasMore = events.size() > limit;
                    if (hasMore) events.remove(events.size()-1);
                    return Optional.of(new History(owner,revision,events,hasMore,
                        hasMore ? events.get(events.size()-1).revision() : null));
                });
        } catch (DataAccessException ex) { throw new ReadUnavailableException(ex); }
    }

    private PanelUserPrincipal boundReader(Authentication authentication) {
        if (authentication==null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
            || !(authentication.getPrincipal() instanceof PanelUserPrincipal reader)
            || reader.getUserId()<=0 || reader.getUsername().isBlank()
            || !reader.isEnabled() || !reader.isAccountNonLocked()
            || !reader.isAccountNonExpired() || !reader.isCredentialsNonExpired()) return null;
        return reader;
    }
    private boolean valid(String publicId, PanelUserPrincipal reader) {
        return publicId != null && publicId.matches("[a-f0-9]{32}") && reader != null;
    }
    private MapSqlParameterSource args(String publicId,PanelUserPrincipal reader) {
        return new MapSqlParameterSource("publicId",publicId).addValue("readerId",reader.getUserId()).addValue("readerName",reader.getUsername())
            .addValue("identity",reader.getUsername().toLowerCase(Locale.ROOT));
    }
    private Owner owner(ResultSet rs) throws SQLException {
        return new Owner(rs.getString("user_id"),rs.getString("ticket_id"),rs.getString("ticket_public_id"),rs.getString("business_id"));
    }
    public record Owner(String userId,String ticketId,String ticketPublicId,String businessId) { }
    public record Member(@JsonSerialize(using=ToStringSerializer.class) long id,String snapshot,String username,
                         boolean eligible,OffsetDateTime addedAt) { }
    public record Roster(Owner owner,@JsonSerialize(using=ToStringSerializer.class) long revision,List<Member> members) {
        public Roster { members = List.copyOf(members); }
    }
    public record Event(@JsonSerialize(using=ToStringSerializer.class) long revision,String action,
                        @JsonSerialize(using=ToStringSerializer.class) long memberId,String member,
                        @JsonSerialize(using=ToStringSerializer.class) long actorId,String actor,OffsetDateTime createdAt) { }
    public record History(Owner owner,@JsonSerialize(using=ToStringSerializer.class) long revision,List<Event> events,
                          boolean hasMore,@JsonSerialize(using=ToStringSerializer.class) Long nextBeforeRevision) {
        public History { events = List.copyOf(events); }
    }
    public static class RosterTooLargeException extends RuntimeException { }
    public static class ReadUnavailableException extends RuntimeException {
        public ReadUnavailableException(Throwable cause) { super("Participant data is unavailable",cause); }
    }
}
