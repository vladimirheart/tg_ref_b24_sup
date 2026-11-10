package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Explicit, version-bound consent to send a reviewed source verbatim. Never a permission to call an LLM. */
@Service
public class ScopedAiReplyRuleService {
    static final String CONFIGURE = """
        actor.enabled=TRUE AND actor.is_blocked=FALSE
        AND 1=(SELECT COUNT(*) FROM users candidate WHERE lower(candidate.username)=lower(actor.username))
        AND EXISTS(SELECT 1 FROM businesses business WHERE business.id=owner.business_id AND business.status='active')
        AND (EXISTS(SELECT 1 FROM business_memberships membership WHERE membership.user_id=actor.id
            AND membership.business_id=owner.business_id AND membership.is_active=TRUE AND membership.role_code='ADMIN')
          OR EXISTS(SELECT 1 FROM business_access_grants access_grant WHERE access_grant.user_id=actor.id
            AND access_grant.grant_scope='ALL_BUSINESSES' AND access_grant.is_active=TRUE AND access_grant.role_code='ADMIN'))
        """;
    private final NamedParameterJdbcTemplate sql;
    private final MultibusinessFeatureFlags flags;
    public ScopedAiReplyRuleService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags) {
        this.sql=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;
    }
    public boolean create(long businessId,Authentication authentication,String key,String trigger,long sourceId,
                          long sourceRevision,boolean reviewed) {
        var args=args(businessId,authentication);
        if(args==null || !reviewed || key==null || !key.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")
            || trigger==null || trigger.trim().isEmpty() || trigger.length()>500 || sourceId<1 || sourceRevision<1
            || !GeneralTicketReplyService.validInput("a".repeat(32),trigger)) return false;
        args.addValue("key",key).addValue("trigger",trigger.replaceAll("^ +| +$","")).addValue("sourceId",sourceId).addValue("sourceRevision",sourceRevision);
        return write("""
            WITH changed AS (
                INSERT INTO ai_scoped_reply_rules(rule_key,business_id,trigger_text,source_id,source_revision,reviewed_by_user_id,reviewed_by)
                SELECT :key,owner.business_id,:trigger,source.id,source.revision,actor.id,actor.username
                FROM (SELECT CAST(:businessId AS BIGINT) AS business_id) owner
                JOIN users actor ON actor.id=:actorId AND actor.username=:actorName
                JOIN ai_scoped_sources source ON source.business_id=owner.business_id AND source.id=:sourceId
                WHERE
            """+CONFIGURE+" AND "+ScopedAiSourceService.CURRENT_ORIGIN+"""
                AND source.revision=:sourceRevision AND source.status='APPROVED'
                AND length(source.body) BETWEEN 1 AND 4000
                ON CONFLICT DO NOTHING RETURNING *
            ) INSERT INTO ai_scoped_reply_rule_events(rule_key,business_id,revision,action,actor_user_id,actor)
                SELECT rule_key,business_id,revision,'reviewed',reviewed_by_user_id,reviewed_by FROM changed
            """,args);
    }
    public boolean revoke(long businessId,Authentication authentication,String key,long revision) {
        var args=args(businessId,authentication);
        if(args==null || key==null || revision<1) return false;
        args.addValue("key",key).addValue("revision",revision);
        return write("""
            WITH changed AS (
                UPDATE ai_scoped_reply_rules rule SET active=FALSE,revision=rule.revision+1
                FROM (SELECT CAST(:businessId AS BIGINT) AS business_id) owner
                JOIN users actor ON actor.id=:actorId AND actor.username=:actorName
                WHERE rule.business_id=owner.business_id AND rule.rule_key=:key AND rule.revision=:revision AND rule.active=TRUE
                AND
            """+CONFIGURE+"""
                RETURNING rule.*,actor.id AS revoker_id,actor.username AS revoker
            ) INSERT INTO ai_scoped_reply_rule_events(rule_key,business_id,revision,action,actor_user_id,actor)
                SELECT rule_key,business_id,revision,'revoked',revoker_id,revoker FROM changed
            """,args);
    }
    public List<Rule> list(long businessId,Authentication authentication,long sourceId,String before) {
        var args=args(businessId,authentication);if(args==null) return List.of();
        args.addValue("sourceId",sourceId).addValue("before",before,java.sql.Types.VARCHAR);
        try { return sql.query("""
            SELECT rule.* FROM ai_scoped_reply_rules rule
            JOIN (SELECT CAST(:businessId AS BIGINT) AS business_id) owner ON rule.business_id=owner.business_id
            JOIN users actor ON actor.id=:actorId AND actor.username=:actorName
            WHERE rule.source_id=:sourceId AND rule.active=TRUE AND (:before IS NULL OR rule.rule_key<:before) AND
            """+CONFIGURE+" ORDER BY rule.rule_key DESC LIMIT 26",
            args,(rs,n)->new Rule(rs.getString("rule_key"),rs.getString("trigger_text"),rs.getLong("source_revision"),rs.getLong("revision")));
        } catch(org.springframework.dao.DataAccessException ex) { return List.of(); }
    }
    private boolean write(String query,MapSqlParameterSource args) {
        try { return sql.update(query,args)==1; } catch(org.springframework.dao.DataAccessException ex) { return false; }
    }
    private MapSqlParameterSource args(long businessId,Authentication auth) {
        if(flags==null || !flags.isFoundationEnabled() || !flags.isUiSelector() || businessId<1 || auth==null
            || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
            || !(auth.getPrincipal() instanceof PanelUserPrincipal actor) || actor.getUserId()<1
            || !actor.isEnabled() || !actor.isAccountNonLocked() || !actor.isAccountNonExpired() || !actor.isCredentialsNonExpired()) return null;
        return new MapSqlParameterSource("businessId",businessId).addValue("actorId",actor.getUserId()).addValue("actorName",actor.getUsername());
    }
    public record Rule(String key,String trigger,long sourceRevision,long revision) {
        @Override public String toString() { return "ScopedAiReplyRule[key="+key+",revision="+revision+"]"; }
    }
}
