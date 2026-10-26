package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Statement-time candidate and workload snapshot, not an assignment authority or a recipient READ grant. */
@Service
public class ScopedAssignmentCandidateService {
    private final JdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;

    public ScopedAssignmentCandidateService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags) {
        this.jdbc=jdbc;this.flags=flags;
    }

    public record Snapshot(boolean legacyUnresolved,Map<String,Long> openCounts) {
        public Snapshot {openCounts=Map.copyOf(openCounts);}
    }

    public Optional<Snapshot> capture(UiEventTicketScope scope,Long channelId) {
        if(scope==null || !scope.isValid() || channelId==null || channelId<=0 || flags==null
                || ("BUSINESS".equals(scope.scopeKind()) && !flags.isFoundationEnabled())) return Optional.empty();
        try {
            var rows=jdbc.query("""
                SELECT candidate.username,workload.open_count
                FROM tickets owner
                JOIN channels channel ON channel.id=owner.channel_id AND channel.id=? AND channel.is_active=TRUE
                LEFT JOIN businesses business ON business.id=owner.business_id AND business.status='active'
                LEFT JOIN users candidate ON business.id IS NOT NULL
                    AND candidate.enabled=TRUE AND candidate.is_blocked=FALSE
                    AND candidate.username IS NOT NULL AND btrim(candidate.username)<>''
                    AND lower(candidate.username)<>'ai_agent'
                    AND 1=(SELECT count(*) FROM users identity_candidate WHERE lower(identity_candidate.username)=lower(candidate.username))
                    AND (EXISTS(SELECT 1 FROM business_memberships membership
                        WHERE membership.user_id=candidate.id AND membership.business_id=owner.business_id
                            AND membership.is_active=TRUE AND membership.role_code IN ('OPERATOR','MANAGER','ADMIN'))
                        OR EXISTS(SELECT 1 FROM business_access_grants access_grant
                            WHERE access_grant.user_id=candidate.id AND access_grant.grant_scope='ALL_BUSINESSES'
                                AND access_grant.is_active=TRUE AND access_grant.role_code IN ('OPERATOR','MANAGER','ADMIN')))
                LEFT JOIN LATERAL (
                    SELECT count(*) AS open_count FROM ticket_responsibles assigned
                    JOIN tickets loaded ON loaded.ticket_id=assigned.ticket_id
                        AND loaded.business_id=owner.business_id AND loaded.is_general=FALSE
                    WHERE lower(assigned.responsible)=lower(candidate.username)
                        AND position('closed' IN lower(btrim(COALESCE(loaded.status,''))))=0
                        AND position('resolved' IN lower(btrim(COALESCE(loaded.status,''))))=0
                        AND 1=(SELECT count(*) FROM tickets duplicate_root WHERE duplicate_root.ticket_id=loaded.ticket_id)
                ) workload ON candidate.username IS NOT NULL
                WHERE owner.user_id=? AND owner.ticket_id=? AND owner.ticket_public_id=?
                    AND owner.business_id IS NOT DISTINCT FROM CAST(? AS BIGINT) AND owner.is_general=FALSE
                    AND CASE WHEN owner.business_id IS NULL THEN 'UNRESOLVED' ELSE 'BUSINESS' END=?
                    AND 1=(SELECT count(*) FROM tickets duplicate_root WHERE duplicate_root.ticket_id=owner.ticket_id)
                    AND (owner.business_id IS NOT NULL OR ?=FALSE)
                ORDER BY lower(candidate.username)
                """,(rs,row)->new Candidate(rs.getString("username"),rs.getLong("open_count")),
                channelId,scope.ticketUserId(),scope.ticketId(),scope.ticketPublicId(),scope.businessId(),scope.scopeKind(),flags.isStrictReadScope());
            if(rows.isEmpty()) return Optional.empty();
            var counts=new LinkedHashMap<String,Long>();
            for(var row:rows) if(org.springframework.util.StringUtils.hasText(row.username())) counts.put(row.username().toLowerCase(java.util.Locale.ROOT),row.openCount());
            return Optional.of(new Snapshot("UNRESOLVED".equals(scope.scopeKind()),counts));
        } catch(DataAccessException ex) {return Optional.empty();}
    }

    private record Candidate(String username,long openCount) {}
}
