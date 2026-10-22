package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.integration.LegacyTicketIdConcurrencyGuard;
import java.util.*;
import java.util.function.Function;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bare-ID compatibility writes never authorize a BUSINESS or GENERAL operation. */
final class LegacyDialogAssignmentWriter {
    private final JdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final LegacyTicketIdConcurrencyGuard guard;
    private final PlatformTransactionManager transactions;
    private static final String OWNER="""
        owner.user_id=? AND owner.ticket_id=? AND owner.ticket_public_id=?
        AND owner.channel_id IS NOT DISTINCT FROM CAST(? AS BIGINT)
        AND owner.responsibility_revision=? AND owner.is_general=FALSE AND owner.business_id IS NULL
        AND position('closed' IN lower(COALESCE(owner.status,'')))=0
        AND position('resolved' IN lower(COALESCE(owner.status,'')))=0
        AND 1=(SELECT count(*) FROM tickets collision WHERE collision.ticket_id=owner.ticket_id)
        """;
    LegacyDialogAssignmentWriter(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,PlatformTransactionManager transactions) {
        this.jdbc=jdbc;this.flags=flags;this.transactions=transactions;guard=new LegacyTicketIdConcurrencyGuard(jdbc);
    }
    private boolean strict(){return flags!=null&&flags.isStrictReadScope();}
    boolean allowed(String raw) {
        if(strict()||raw==null||raw.isBlank())return false;
        try {
            var rows=jdbc.query("SELECT user_id,ticket_public_id,is_general,business_id FROM tickets WHERE ticket_id=? LIMIT 2",
                (rs,n)->rs.getObject("user_id")!=null && Boolean.FALSE.equals(rs.getObject("is_general",Boolean.class))
                    && rs.getObject("business_id")==null && rs.getString("ticket_public_id")!=null
                    && rs.getString("ticket_public_id").matches("[a-f0-9]{32}"),raw);
            return rows.size()==1&&rows.get(0)&&!strict();
        }catch(DataAccessException ex){return false;}
    }
    <T> T execute(String raw,String target,String actor,boolean redirect,Function<DialogResponsibilityService.LegacyAssignment,T> after) {
        var denied=new DialogResponsibilityService.LegacyAssignment(false,false,null);
        if(strict()||raw==null||raw.isBlank())return after.apply(denied);
        PlatformTransactionManager manager=transactions;
        if(manager==null&&jdbc.getDataSource()!=null)manager=new DataSourceTransactionManager(jdbc.getDataSource());
        if(manager==null)return after.apply(denied);
        boolean[] callback={false};
        try {
            var tx=new TransactionTemplate(manager);
            return tx.execute(status->{
                guard.acquire(raw);
                var roots=jdbc.query("""
                    SELECT owner.user_id,owner.ticket_public_id,owner.channel_id,owner.responsibility_revision,
                           owner.is_general,owner.business_id,owner.status,assigned.responsible
                      FROM tickets owner LEFT JOIN ticket_responsibles assigned ON assigned.ticket_id=owner.ticket_id
                     WHERE owner.ticket_id=? FOR UPDATE OF owner
                    """,(rs,n)->new Root(rs.getObject("user_id",Long.class),rs.getString("ticket_public_id"),
                        rs.getObject("channel_id",Long.class),rs.getLong("responsibility_revision"),
                        rs.getObject("is_general",Boolean.class),rs.getObject("business_id",Long.class),
                        rs.getString("status"),rs.getString("responsible")),raw);
                if(roots.size()!=1||!roots.get(0).namespaceValid()||strict()) {
                    callback[0]=true;return after.apply(denied);
                }
                var root=roots.get(0);
                if(!root.valid()) {
                    // Legacy relay may inspect an existing owner of a closed UNRESOLVED root.
                    // It cannot create/change ownership or enter the redirect success workflow.
                    callback[0]=true;
                    return after.apply(!redirect&&root.responsible()!=null
                            ?new DialogResponsibilityService.LegacyAssignment(true,false,trim(root.responsible())):denied);
                }
                int changed=0;
                boolean writable=target!=null&&!target.isBlank() && (redirect||!"ai_agent".equalsIgnoreCase(target.trim()));
                if(redirect&&Objects.equals(root.responsible(),target))writable=false;
                if(writable) {
                    var args=new ArrayList<Object>(Arrays.asList(raw,target,actor,raw));root.bind(args,raw);
                    changed=jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) "
                        +"SELECT assignment.* FROM (VALUES(?::text,?::text,?::text)) AS assignment(ticket_id,responsible,assigned_by) "
                        +"WHERE NOT EXISTS(SELECT 1 FROM ticket_responsibles existing WHERE existing.ticket_id=?) "
                        +"AND EXISTS(SELECT 1 FROM tickets owner WHERE "+OWNER+")",args.toArray());
                    if(changed==0&&redirect&&!strict()) {
                        args=new ArrayList<>(Arrays.asList(target,actor,raw,root.responsible()));root.bind(args,raw);
                        changed=jdbc.update("UPDATE ticket_responsibles SET responsible=?,assigned_by=? WHERE ticket_id=? "
                            +"AND responsible IS NOT DISTINCT FROM CAST(? AS TEXT) AND EXISTS(SELECT 1 FROM tickets owner WHERE "+OWNER+")",args.toArray());
                    }
                }
                // A trigger or a concurrent legacy statement must not turn a rejected write into success.
                var confirmed=jdbc.query("""
                    SELECT owner.user_id,owner.ticket_public_id,owner.channel_id,owner.is_general,owner.business_id,
                           owner.status,assigned.responsible,owner.responsibility_revision
                      FROM tickets owner LEFT JOIN ticket_responsibles assigned ON assigned.ticket_id=owner.ticket_id
                     WHERE owner.ticket_id=?
                    """,(rs,n)->new Root(rs.getObject("user_id",Long.class),rs.getString("ticket_public_id"),
                        rs.getObject("channel_id",Long.class),rs.getLong("responsibility_revision"),
                        rs.getObject("is_general",Boolean.class),rs.getObject("business_id",Long.class),
                        rs.getString("status"),rs.getString("responsible")),raw);
                if(strict()||confirmed.size()!=1||!root.sameOwner(confirmed.get(0))||!confirmed.get(0).valid()
                        ||(changed==0&&root.revision()!=confirmed.get(0).revision())
                        || (changed>0&&!Objects.equals(target,confirmed.get(0).responsible()))) {
                    status.setRollbackOnly();callback[0]=true;return after.apply(denied);
                }
                callback[0]=true;
                return after.apply(new DialogResponsibilityService.LegacyAssignment(true,changed>0,trim(confirmed.get(0).responsible())));
            });
        }catch(DataAccessException ex){if(callback[0])throw ex;return after.apply(denied);}
    }
    private static String trim(String value){return value==null||value.isBlank()?null:value.trim();}
    private record Root(Long user,String publicId,Long channel,long revision,Boolean general,Long business,String status,String responsible) {
        boolean namespaceValid(){return user!=null&&publicId!=null&&publicId.matches("[a-f0-9]{32}")
            && revision>=0&&Boolean.FALSE.equals(general)&&business==null;}
        boolean valid(){String s=status==null?"":status.toLowerCase(Locale.ROOT);return namespaceValid()&&!s.contains("closed")&&!s.contains("resolved");}
        boolean sameOwner(Root other){return Objects.equals(user,other.user)&&Objects.equals(publicId,other.publicId)&&Objects.equals(channel,other.channel);}
        void bind(List<Object> args,String raw){Collections.addAll(args,user,raw,publicId,channel,revision);}
    }
}
