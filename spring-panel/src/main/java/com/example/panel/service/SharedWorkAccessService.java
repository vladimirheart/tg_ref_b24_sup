package com.example.panel.service;

import com.example.panel.security.PanelRolePermissionSnapshot;
import java.sql.Types;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

/** Independent shared-work grants and numeric membership. Never infers access from business or display names. */
@Service
@Transactional
public class SharedWorkAccessService {
    public enum Kind {
        TASK("tasks","shared_task_members","task_id","can_create_task"),
        PROJECT("projects","shared_project_members","project_id","can_create_project");
        final String roots,members,key,create;
        Kind(String roots,String members,String key,String create){this.roots=roots;this.members=members;this.key=key;this.create=create;}
    }
    public enum Access { READ, WRITE, OWNER }
    public enum Role { VIEWER, EDITOR }
    public record Grant(boolean canRead,boolean canCreateTask,boolean canCreateProject) {}
    public record AccountGrant(String userId,String username,boolean canRead,boolean canCreateTask,boolean canCreateProject) {}
    public record Member(String userId,String username,String role) {}

    private static final String ACCOUNT = GeneralTicketAccessService.CURRENT_ACCOUNT;
    // Same current assigned-role proof as GENERAL administration. No username/admin/business bypass.
    private static final String ADMIN = """
        WITH actor AS MATERIALIZED (
            SELECT account.id,account.role_id FROM users account
            WHERE account.id=:authenticatedActorId AND account.username=:identity AND
        """+ACCOUNT+"""
            FOR SHARE OF account
        ), assigned AS MATERIALIZED (
            SELECT role.id FROM roles role JOIN actor ON role.id=actor.role_id
            WHERE role.id=:assignedRoleId AND role.name IS NOT DISTINCT FROM :assignedRoleName
              AND role.permissions IS NOT DISTINCT FROM :assignedPermissions FOR SHARE OF role
        ), direct_admin AS MATERIALIZED (
            SELECT authority.user_id FROM user_authorities authority JOIN actor ON actor.id=authority.user_id
            WHERE authority.authority='ROLE_ADMIN' FOR SHARE OF authority
        ), direct_settings AS MATERIALIZED (
            SELECT authority.user_id FROM user_authorities authority JOIN actor ON actor.id=authority.user_id
            WHERE authority.authority='PAGE_SETTINGS' FOR SHARE OF authority
        ), administrator AS MATERIALIZED (
            SELECT actor.id FROM actor
            WHERE (EXISTS(SELECT 1 FROM direct_admin) OR (:assignedAdmin AND EXISTS(SELECT 1 FROM assigned)))
              AND (EXISTS(SELECT 1 FROM direct_settings) OR (:assignedSettings AND EXISTS(SELECT 1 FROM assigned)))
        )
        """;
    private static final String READER = """
        WITH reader AS MATERIALIZED (
            SELECT account.id,permission.can_create_task,permission.can_create_project
              FROM users account JOIN shared_work_grants permission ON permission.user_id=account.id
            WHERE account.id=:authenticatedActorId AND account.username=:identity AND permission.can_read AND
        """+ACCOUNT+"""
            FOR SHARE OF account,permission
        )
        """;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate sql;
    public SharedWorkAccessService(JdbcTemplate jdbc){this.jdbc=jdbc;this.sql=new NamedParameterJdbcTemplate(jdbc);}

    public List<AccountGrant> accounts(Authentication authentication) {
        var result=sql.query(ADMIN+"""
            SELECT account.id,account.username,COALESCE(permission.can_read,FALSE) can_read,
                   COALESCE(permission.can_create_task,FALSE) can_create_task,
                   COALESCE(permission.can_create_project,FALSE) can_create_project
              FROM administrator CROSS JOIN users account
              LEFT JOIN shared_work_grants permission ON permission.user_id=account.id
             ORDER BY account.username,account.id
            """,adminArgs(authentication),(rs,row)->new AccountGrant(rs.getString("id"),rs.getString("username"),
                rs.getBoolean("can_read"),rs.getBoolean("can_create_task"),rs.getBoolean("can_create_project")));
        if(result.isEmpty())throw administratorDenied();
        return result;
    }

    public void setGrant(Authentication authentication,long targetId,Grant grant) {
        positive(targetId);
        if(grant==null||(!grant.canRead()&&(grant.canCreateTask()||grant.canCreateProject())))throw badRequest();
        var args=adminArgs(authentication).addValue("targetId",targetId,Types.BIGINT)
            .addValue("read",grant.canRead()).addValue("task",grant.canCreateTask()).addValue("project",grant.canCreateProject());
        var result=sql.queryForMap(ADMIN+"""
            , changed AS (
                INSERT INTO shared_work_grants(user_id,can_read,can_create_task,can_create_project)
                SELECT account.id,:read,:task,:project FROM users account CROSS JOIN administrator
                 WHERE account.id=:targetId
                ON CONFLICT(user_id) DO UPDATE SET can_read=EXCLUDED.can_read,
                    can_create_task=EXCLUDED.can_create_task,can_create_project=EXCLUDED.can_create_project,
                    updated_at=CURRENT_TIMESTAMP RETURNING user_id
            )
            SELECT EXISTS(SELECT 1 FROM administrator) authorized,EXISTS(SELECT 1 FROM changed) changed
            """,args);
        if(!Boolean.TRUE.equals(result.get("authorized")))throw administratorDenied();
        if(!Boolean.TRUE.equals(result.get("changed")))throw missing();
    }

    public void require(Kind kind,long id,Access access,Authentication authentication) {
        lock(kind,id,access,authentication);
    }

    /** Trusted creation writer only: owner FK must already be written from the authenticated numeric actor in the same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerCreated(Kind kind,long id,Authentication authentication) {
        positive(id);var args=actorArgs(authentication).addValue("id",id,Types.BIGINT);
        String query=READER+"""
            , owned AS MATERIALIZED (
                SELECT root.id,root.shared_owner_user_id FROM %s root JOIN reader ON root.shared_owner_user_id=reader.id
                WHERE root.id=:id AND reader.%s FOR UPDATE OF root
            ), changed AS (
                INSERT INTO %s(%s,user_id,access_role) SELECT id,shared_owner_user_id,'OWNER' FROM owned
                ON CONFLICT(%s,user_id) DO NOTHING RETURNING user_id
            )
            SELECT EXISTS(SELECT 1 FROM changed) OR EXISTS(
                SELECT 1 FROM owned JOIN %s member ON member.%s=owned.id
                WHERE member.user_id=owned.shared_owner_user_id AND member.access_role='OWNER')
            """;
        if(!Boolean.TRUE.equals(sql.queryForObject(query.formatted(kind.roots,kind.create,kind.members,kind.key,kind.key,kind.members,kind.key),args,Boolean.class)))throw missing();
    }

    public List<String> visibleIds(Kind kind,Authentication authentication) {
        return sql.query(READER+"""
            SELECT root.id FROM %s root JOIN %s member ON member.%s=root.id JOIN reader ON reader.id=member.user_id
             WHERE root.shared_owner_user_id IS NOT NULL
               AND (member.access_role<>'OWNER' OR member.user_id=root.shared_owner_user_id) ORDER BY root.id
            """.formatted(kind.roots,kind.members,kind.key),actorArgs(authentication),(rs,row)->rs.getString("id"));
    }

    public List<Member> members(Kind kind,long id,Authentication authentication) {
        lock(kind,id,Access.READ,authentication);
        return sql.query("""
            SELECT account.id,account.username,member.access_role FROM %s member JOIN users account ON account.id=member.user_id
             WHERE member.%s=:id ORDER BY account.id
            """.formatted(kind.members,kind.key),new MapSqlParameterSource("id",id),
            (rs,row)->new Member(rs.getString("id"),rs.getString("username"),rs.getString("access_role")));
    }

    public void setMember(Kind kind,long id,long targetId,Role role,Authentication authentication) {
        positive(targetId);if(role==null)throw badRequest();
        long owner=lock(kind,id,Access.OWNER,authentication);
        if(targetId==owner)throw new ResponseStatusException(HttpStatus.CONFLICT,"Владелец не изменяется через состав участников");
        var args=new MapSqlParameterSource("id",id).addValue("targetId",targetId).addValue("role",role.name());
        int changed=sql.update("""
            WITH eligible AS MATERIALIZED (
            SELECT account.id FROM users account JOIN shared_work_grants permission ON permission.user_id=account.id
             WHERE account.id=:targetId AND permission.can_read AND
            """+ACCOUNT+"""
            FOR SHARE OF account,permission
            )
            INSERT INTO %s(%s,user_id,access_role) SELECT :id,id,:role FROM eligible
            ON CONFLICT(%s,user_id) DO UPDATE SET access_role=EXCLUDED.access_role,updated_at=CURRENT_TIMESTAMP
            """.formatted(kind.members,kind.key,kind.key),args);
        if(changed!=1)throw missing();
    }

    public void removeMember(Kind kind,long id,long targetId,Authentication authentication) {
        positive(targetId);long owner=lock(kind,id,Access.OWNER,authentication);
        if(targetId==owner)throw new ResponseStatusException(HttpStatus.CONFLICT,"Нельзя удалить владельца");
        sql.update("DELETE FROM "+kind.members+" WHERE "+kind.key+"=:id AND user_id=:targetId",
            new MapSqlParameterSource("id",id).addValue("targetId",targetId));
    }

    private long lock(Kind kind,long id,Access access,Authentication authentication) {
        positive(id);var args=actorArgs(authentication).addValue("id",id);
        String roles=switch(access){case READ->"'OWNER','EDITOR','VIEWER'";case WRITE->"'OWNER','EDITOR'";case OWNER->"'OWNER'";};
        String rootLock=access==Access.READ?"SHARE":"UPDATE";
        var owners=sql.query(READER+"""
            , owned AS MATERIALIZED (
                SELECT root.id,root.shared_owner_user_id FROM %s root CROSS JOIN reader
                 WHERE root.id=:id AND root.shared_owner_user_id IS NOT NULL FOR %s OF root
            )
            SELECT owned.shared_owner_user_id FROM owned JOIN %s member ON member.%s=owned.id JOIN reader ON reader.id=member.user_id
             WHERE member.access_role IN (%s)
               AND (member.access_role<>'OWNER' OR member.user_id=owned.shared_owner_user_id)
             FOR SHARE OF member
            """.formatted(kind.roots,rootLock,kind.members,kind.key,roles),args,(rs,row)->rs.getLong(1));
        if(owners.size()!=1)throw missing();
        return owners.get(0);
    }

    private MapSqlParameterSource actorArgs(Authentication authentication) {
        var actor=GeneralTicketActor.from(authentication);
        if(actor==null||!actor.valid())throw missing();
        return actor.bind(new MapSqlParameterSource());
    }
    private MapSqlParameterSource adminArgs(Authentication authentication) {
        var actor=GeneralTicketActor.from(authentication);
        if(actor==null||!actor.valid())throw administratorDenied();
        return PanelRolePermissionSnapshot.capture(jdbc,actor.userId(),actor.username()).bind(actor.bind(new MapSqlParameterSource()));
    }
    private static void positive(long id){if(id<=0)throw badRequest();}
    private static ResponseStatusException badRequest(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Некорректные параметры доступа");}
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Объект недоступен");}
    private static AccessDeniedException administratorDenied(){return new AccessDeniedException("Требуются действующие ROLE_ADMIN и PAGE_SETTINGS");}
}
