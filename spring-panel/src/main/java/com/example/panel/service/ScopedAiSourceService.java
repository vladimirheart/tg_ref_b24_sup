package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.NotificationBusinessAccessSql;
import com.example.panel.repository.ScopedAiSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Internal curated-source APIs. HTTP callers still need page authority and CSRF. No global/legacy fallback. */
@Service
public class ScopedAiSourceService {
    private static final String ACTOR = " CROSS JOIN (SELECT CAST(:identity AS TEXT) AS user_identity) n ";
    private static final String READ = " AND ("+NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS+")";
    private static final String CONFIGURE = """
        AND (EXISTS (SELECT 1 FROM business_memberships membership
            WHERE membership.user_id=actor.id AND membership.business_id=owner.business_id
              AND membership.is_active=TRUE AND membership.role_code='ADMIN')
          OR EXISTS (SELECT 1 FROM business_access_grants access_grant
            WHERE access_grant.user_id=actor.id AND access_grant.grant_scope='ALL_BUSINESSES'
              AND access_grant.is_active=TRUE AND access_grant.role_code='ADMIN'))
        """;
    private static final String ORIGIN_COLUMNS = "origin_ticket_user_id,origin_ticket_id,origin_ticket_public_id,origin_ticket_scope_kind,origin_ticket_business_id";
    static final String CURRENT_ORIGIN = """
        (source.source_kind='KNOWLEDGE' OR EXISTS (SELECT 1 FROM tickets provenance
            WHERE provenance.user_id=source.origin_ticket_user_id AND provenance.ticket_id=source.origin_ticket_id
              AND provenance.ticket_public_id=source.origin_ticket_public_id AND provenance.business_id=source.business_id
              AND provenance.business_id=source.origin_ticket_business_id AND source.origin_ticket_scope_kind='BUSINESS'
              AND provenance.is_general=FALSE
              AND 1=(SELECT COUNT(*) FROM tickets candidate WHERE candidate.ticket_id=provenance.ticket_id)))
        """;
    private static final String SOURCE_KEY = "source.business_id=:businessId AND source.source_kind=:kind AND source.source_key=:key";
    private final NamedParameterJdbcOperations jdbc;
    private final MultibusinessFeatureFlags flags;

    @Autowired
    public ScopedAiSourceService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags) {
        this(new NamedParameterJdbcTemplate(jdbc),flags);
    }

    ScopedAiSourceService(NamedParameterJdbcOperations jdbc,MultibusinessFeatureFlags flags) {
        this.jdbc=jdbc;
        this.flags=flags;
    }

    public boolean createKnowledge(Long businessId,String identity,String key,String title,String body) {
        return create(businessId,identity,Kind.KNOWLEDGE,key,title,body,null);
    }

    /** Manually curated memory with a proven origin; this does not learn from legacy raw history. */
    public boolean createMemory(UiEventTicketScope origin,String identity,String key,String title,String body) {
        if (!validScope(origin)) return false;
        return create(origin.businessId(),identity,Kind.MEMORY,key,title,body,origin);
    }

    private boolean create(Long businessId,String identity,Kind kind,String key,String title,String body,UiEventTicketScope origin) {
        if (!validActor(businessId,identity) || !validContent(key,title,body)) return false;
        var args=parameters(businessId,identity,kind,key).addValue("title",title.trim(),Types.VARCHAR)
            .addValue("body",body.trim(),Types.VARCHAR);
        String from=" FROM (SELECT CAST(:businessId AS BIGINT) AS business_id) owner";
        String originValues="NULL,NULL,NULL,NULL,NULL";
        String proof="TRUE";
        if (origin!=null) {
            scopeParameters(args,origin);
            from=" FROM tickets owner";
            originValues="owner.user_id,owner.ticket_id,owner.ticket_public_id,'BUSINESS',owner.business_id";
            proof=ScopedAiSql.CURRENT_OWNER;
        }
        String sql="WITH changed AS (INSERT INTO ai_scoped_sources(business_id,source_kind,source_key,title,body,status,revision,"
            +ORIGIN_COLUMNS+",updated_by_user_id,updated_by) SELECT owner.business_id,:kind,:key,:title,:body,'DRAFT',1,"
            +originValues+",actor.id,n.user_identity"+from+ACTOR+" JOIN users actor ON lower(actor.username)=n.user_identity WHERE "
            +proof+READ+CONFIGURE+" ON CONFLICT (business_id,source_kind,source_key) DO NOTHING RETURNING *)"
            +auditInsert("create");
        return write(sql,args);
    }

    /** Explicit review/update uses compare-and-set; approval never follows an unreviewed new revision. */
    public boolean revise(Long businessId,String identity,Kind kind,String key,long expectedRevision,
                           String title,String body,Status status) {
        return reviseInternal(businessId,identity,kind,key,null,expectedRevision,title,body,status);
    }

    /** HTTP forms also freeze the immutable source ID, since a key/revision can recur in another business. */
    public boolean reviseExpected(Long businessId,String identity,Kind kind,String key,long expectedSourceId,
                                   long expectedRevision,String title,String body,Status status) {
        if (expectedSourceId<1) return false;
        return reviseInternal(businessId,identity,kind,key,expectedSourceId,expectedRevision,title,body,status);
    }

    private boolean reviseInternal(Long businessId,String identity,Kind kind,String key,Long expectedSourceId,
                                    long expectedRevision,String title,String body,Status status) {
        if (!validActor(businessId,identity) || kind==null || status==null || expectedRevision<1
                || !validContent(key,title,body)) return false;
        var args=parameters(businessId,identity,kind,key).addValue("revision",expectedRevision,Types.BIGINT)
            .addValue("title",title.trim(),Types.VARCHAR).addValue("body",body.trim(),Types.VARCHAR)
            .addValue("status",status.name(),Types.VARCHAR).addValue("expectedSourceId",expectedSourceId,Types.BIGINT);
        String sql="""
            WITH changed AS (UPDATE ai_scoped_sources source SET title=:title,body=:body,status=:status,
                revision=source.revision+1,updated_by_user_id=actor.id,updated_by=n.user_identity,updated_at=CURRENT_TIMESTAMP
              FROM (SELECT CAST(:businessId AS BIGINT) AS business_id) owner
            """+ACTOR+" JOIN users actor ON lower(actor.username)=n.user_identity WHERE "+SOURCE_KEY
            +" AND source.revision=:revision"+(expectedSourceId==null ? "" : " AND source.id=:expectedSourceId")
            +" AND "+CURRENT_ORIGIN+READ+CONFIGURE+" RETURNING source.*)"+auditInsert("revise");
        return write(sql,args);
    }

    public List<BusinessOption> availableBusinesses(String identity) {
        if (flags==null || !StringUtils.hasText(identity)) return List.of();
        String sql="SELECT owner.business_id,owner.display_name FROM (SELECT id AS business_id,display_name FROM businesses) owner"
            +ACTOR+" WHERE TRUE"+READ+" ORDER BY owner.display_name,owner.business_id";
        try {
            return jdbc.query(sql,parameters(null,identity,null,null),
                (rs,row)->new BusinessOption(rs.getLong("business_id"),rs.getString("display_name")));
        } catch (DataAccessException ex) { return List.of(); }
    }

    public boolean canConfigure(Long businessId,String identity) {
        if (!validActor(businessId,identity)) return false;
        String sql="SELECT EXISTS (SELECT 1 FROM (SELECT CAST(:businessId AS BIGINT) AS business_id) owner"+ACTOR
            +" JOIN users actor ON lower(actor.username)=n.user_identity WHERE TRUE"+READ+CONFIGURE+")";
        try { return Boolean.TRUE.equals(jdbc.queryForObject(sql,parameters(businessId,identity,null,null),Boolean.class)); }
        catch (DataAccessException ex) { return false; }
    }

    /** Catalog summaries omit body/origin and filter live READ/current origin before keyset pagination. */
    public List<Summary> catalog(Long businessId,String identity,Kind kind,Status status,String query,Long before,int limit) {
        if (!validActor(businessId,identity) || query==null || query.length()>200 || (before!=null && before<1)) return List.of();
        String sql="""
            SELECT source.id,source.source_kind,source.source_key,source.title,source.status,source.revision
              FROM ai_scoped_sources source JOIN (SELECT CAST(:businessId AS BIGINT) AS business_id) owner
                ON owner.business_id=source.business_id
            """+ACTOR+" WHERE source.business_id=:businessId AND "+CURRENT_ORIGIN+READ+"""
              AND (CAST(:kind AS TEXT) IS NULL OR source.source_kind=:kind)
              AND (CAST(:status AS TEXT) IS NULL OR source.status=:status)
              AND (:query='' OR POSITION(lower(:query) IN lower(source.title||' '||source.body))>0)
              AND (CAST(:before AS BIGINT) IS NULL OR source.id<:before)
            ORDER BY source.id DESC LIMIT :limit
            """;
        var args=parameters(businessId,identity,kind,null).addValue("status",status==null?null:status.name(),Types.VARCHAR)
            .addValue("query",query.trim(),Types.VARCHAR).addValue("before",before,Types.BIGINT)
            .addValue("limit",bounded(limit,51),Types.INTEGER);
        try {
            return jdbc.query(sql,args,(rs,row)->new Summary(rs.getLong("id"),Kind.valueOf(rs.getString("source_kind")),
                rs.getString("source_key"),rs.getString("title"),Status.valueOf(rs.getString("status")),rs.getLong("revision")));
        } catch (DataAccessException ex) { return List.of(); }
    }

    public Optional<Source> find(Long businessId,String identity,Kind kind,String key) {
        if (!validActor(businessId,identity) || kind==null || !validKey(key)) return Optional.empty();
        String sql="SELECT source.* FROM ai_scoped_sources source JOIN (SELECT CAST(:businessId AS BIGINT) AS business_id) owner"
            +" ON owner.business_id=source.business_id"+ACTOR+" WHERE "+SOURCE_KEY+" AND "+CURRENT_ORIGIN+READ;
        var rows=read(sql,parameters(businessId,identity,kind,key));
        return rows.size()==1 ? Optional.of(rows.get(0)) : Optional.empty();
    }

    public List<Map<String,Object>> history(Long businessId,String identity,Kind kind,String key,int limit) {
        if (!validActor(businessId,identity) || kind==null || !validKey(key)) return List.of();
        String sql="""
            SELECT event.revision,event.status,event.action,event.actor,event.created_at
              FROM ai_scoped_source_events event JOIN ai_scoped_sources source ON source.id=event.source_id
                AND source.business_id=event.business_id AND source.source_kind=event.source_kind AND source.source_key=event.source_key
              JOIN (SELECT CAST(:businessId AS BIGINT) AS business_id) owner ON owner.business_id=source.business_id
            """+ACTOR+" WHERE "+SOURCE_KEY+" AND "+CURRENT_ORIGIN+READ+" ORDER BY event.id DESC LIMIT :limit";
        try {
            return jdbc.queryForList(sql,parameters(businessId,identity,kind,key).addValue("limit",bounded(limit,50),Types.INTEGER));
        } catch (DataAccessException ex) { return List.of(); }
    }

    /** Only approved sources of this exact BUSINESS enter the result, before ordering/limit. */
    public List<Source> retrieve(UiEventTicketScope scope,String identity,String query,int limit) {
        if (!validScope(scope) || !StringUtils.hasText(identity) || !StringUtils.hasText(query) || query.length()>500) return List.of();
        String sql="SELECT source.* FROM tickets owner JOIN ai_scoped_sources source ON source.business_id=owner.business_id"
            +ACTOR+" WHERE "+ScopedAiSql.CURRENT_OWNER+READ+" AND ("+ScopedAiSql.CAN_ESCALATE+")"
            +" AND source.status='APPROVED' AND "+CURRENT_ORIGIN
            +" AND POSITION(lower(:query) IN lower(source.title||' '||source.body))>0 ORDER BY source.updated_at DESC,source.id DESC LIMIT :limit";
        var args=scopeParameters(parameters(scope.businessId(),identity,null,null),scope)
            .addValue("query",query.trim(),Types.VARCHAR).addValue("limit",bounded(limit,20),Types.INTEGER);
        return read(sql,args);
    }

    /** Reprove the exact retrieved payload and live authority before a future handoff. This is not a network lock. */
    public boolean isCurrentCandidate(UiEventTicketScope scope,String identity,Source expected) {
        if (!validScope(scope) || !StringUtils.hasText(identity) || expected==null || expected.kind()==null
                || expected.status()!=Status.APPROVED || !scope.businessId().equals(expected.businessId())) return false;
        var args=scopeParameters(parameters(scope.businessId(),identity,expected.kind(),expected.key()),scope)
            .addValue("sourceId",expected.id(),Types.BIGINT).addValue("revision",expected.revision(),Types.BIGINT)
            .addValue("title",expected.title(),Types.VARCHAR).addValue("body",expected.body(),Types.VARCHAR);
        UiEventTicketScope origin=expected.origin();
        args.addValue("originUser",origin==null?null:origin.ticketUserId(),Types.BIGINT)
            .addValue("originRaw",origin==null?null:origin.ticketId(),Types.VARCHAR)
            .addValue("originPublic",origin==null?null:origin.ticketPublicId(),Types.VARCHAR)
            .addValue("originKind",origin==null?null:origin.scopeKind(),Types.VARCHAR)
            .addValue("originBusiness",origin==null?null:origin.businessId(),Types.BIGINT);
        String sql="SELECT EXISTS (SELECT 1 FROM tickets owner JOIN ai_scoped_sources source ON source.business_id=owner.business_id"
            +ACTOR+" WHERE "+ScopedAiSql.CURRENT_OWNER+READ+" AND ("+ScopedAiSql.CAN_ESCALATE+") AND "+SOURCE_KEY
            +" AND source.id=:sourceId AND source.revision=:revision AND source.title=:title AND source.body=:body"
            +" AND source.status='APPROVED' AND "+CURRENT_ORIGIN
            +" AND ROW("+ORIGIN_COLUMNS+") IS NOT DISTINCT FROM ROW(CAST(:originUser AS BIGINT),CAST(:originRaw AS TEXT),"
            +"CAST(:originPublic AS TEXT),CAST(:originKind AS TEXT),CAST(:originBusiness AS BIGINT)))";
        try { return Boolean.TRUE.equals(jdbc.queryForObject(sql,args,Boolean.class)); }
        catch (DataAccessException ex) { return false; }
    }

    private String auditInsert(String action) {
        return " INSERT INTO ai_scoped_source_events(source_id,business_id,source_kind,source_key,revision,status,action,actor_user_id,actor)"
            +" SELECT id,business_id,source_kind,source_key,revision,status,'"+action+"',updated_by_user_id,updated_by FROM changed";
    }

    private boolean write(String sql,MapSqlParameterSource args) {
        try { return jdbc.update(sql,args)==1; }
        catch (DataAccessException ex) { return false; }
    }

    private List<Source> read(String sql,MapSqlParameterSource args) {
        try { return jdbc.query(sql,args,(rs,row)->map(rs)); }
        catch (DataAccessException ex) { return List.of(); }
    }

    private Source map(ResultSet rs) throws SQLException {
        Kind kind=Kind.valueOf(rs.getString("source_kind"));
        UiEventTicketScope origin=kind==Kind.MEMORY ? new UiEventTicketScope(rs.getLong("origin_ticket_user_id"),
            rs.getString("origin_ticket_id"),rs.getString("origin_ticket_public_id"),rs.getString("origin_ticket_scope_kind"),
            rs.getLong("origin_ticket_business_id")) : null;
        return new Source(rs.getLong("id"),rs.getLong("business_id"),kind,rs.getString("source_key"),rs.getString("title"),
            rs.getString("body"),Status.valueOf(rs.getString("status")),rs.getLong("revision"),origin);
    }

    private boolean validActor(Long businessId,String identity) {
        return flags!=null && businessId!=null && businessId>0 && StringUtils.hasText(identity);
    }

    private boolean validScope(UiEventTicketScope scope) {
        return flags!=null && scope!=null && scope.isValid() && "BUSINESS".equals(scope.scopeKind());
    }

    private boolean validKey(String key) { return StringUtils.hasText(key) && key.length()<=200; }
    private boolean validContent(String key,String title,String body) {
        return validKey(key) && StringUtils.hasText(title) && title.length()<=300 && StringUtils.hasText(body) && body.length()<=16000;
    }
    private int bounded(int limit,int max) { return Math.max(1,Math.min(limit,max)); }

    private MapSqlParameterSource parameters(Long businessId,String identity,Kind kind,String key) {
        return new MapSqlParameterSource().addValue("businessId",businessId,Types.BIGINT)
            .addValue("identity",identity.toLowerCase(Locale.ROOT),Types.VARCHAR)
            .addValue("kind",kind==null?null:kind.name(),Types.VARCHAR).addValue("key",key,Types.VARCHAR);
    }

    private MapSqlParameterSource scopeParameters(MapSqlParameterSource args,UiEventTicketScope scope) {
        return args.addValue("ticketUserId",scope.ticketUserId(),Types.BIGINT).addValue("ticketId",scope.ticketId(),Types.VARCHAR)
            .addValue("publicId",scope.ticketPublicId(),Types.VARCHAR).addValue("scopeKind",scope.scopeKind(),Types.VARCHAR)
            .addValue("strict",flags.isStrictReadScope(),Types.BOOLEAN);
    }

    public enum Kind { KNOWLEDGE,MEMORY }
    public enum Status { DRAFT,APPROVED,ARCHIVED }
    public record BusinessOption(long id,String displayName) { }
    public record Summary(long id,Kind kind,String key,String title,Status status,long revision) { }
    public record Source(long id,Long businessId,Kind kind,String key,String title,String body,Status status,long revision,UiEventTicketScope origin) {
        @Override public String toString() { return "ScopedAiSource[id="+id+",businessId="+businessId+",kind="+kind+",revision="+revision+"]"; }
    }
}
