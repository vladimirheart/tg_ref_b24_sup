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

    public record Capabilities(String userId,boolean systemAdmin,boolean canCreateTask,boolean canCreateProject) {}
    public record ObjectAccess(String kind,String id,boolean canWrite,boolean canManage,List<Member> members) {}
    public record Person(String userId,String username) {}

    public Capabilities capabilities(Authentication authentication) {
        var result=sql.query(READER+"SELECT id,system_admin,can_create_task,can_create_project FROM reader",
            actorArgs(authentication),(rs,row)->new Capabilities(rs.getString("id"),rs.getBoolean("system_admin"),
                rs.getBoolean("can_create_task"),rs.getBoolean("can_create_project")));
        if(result.size()!=1)throw missing();return result.get(0);
    }
    public ObjectAccess objectAccess(Kind kind,long id,Authentication authentication) {
        var roster=members(kind,id,authentication);
        boolean administrator=isSystemAdministrator(authentication);
        String actorId=Long.toString(GeneralTicketActor.from(authentication).userId());
        String role=roster.stream().filter(member->member.userId().equals(actorId)).map(Member::role).findFirst().orElse("");
        return new ObjectAccess(kind.name(),Long.toString(id),administrator||role.equals("OWNER")||role.equals("EDITOR"),
            administrator||role.equals("OWNER"),roster);
    }
    /** Candidate lookup is owner/admin only; labels never substitute for numeric identity. */
    public List<Person> people(Kind kind,long id,String query,Authentication authentication) {
        var access=objectAccess(kind,id,authentication);
        if(!access.canManage())throw missing();
        if(query==null)query="";
        if(query.length()>100)throw badRequest();
        var candidates=jdbc.query("SELECT account.id,account.username FROM users account WHERE "+ACCOUNT+
            " AND position(lower(?) IN lower(account.username))>0 ORDER BY account.id LIMIT 100",
            (rs,row)->new Person(rs.getString(1),rs.getString(2)),query);
        var result=new java.util.ArrayList<Person>();
        for(var person:candidates){
            try{requireEligibleTarget(Long.parseLong(person.userId()));result.add(person);}
            catch(ResponseStatusException denied){if(denied.getStatusCode().value()!=404)throw denied;}
        }
        return result;
    }

    private static final String ACCOUNT = GeneralTicketAccessService.CURRENT_ACCOUNT;
    // System administrator is unrestricted within shared work. Business/portal roles never imply this authority.
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
        ), administrator AS MATERIALIZED (
            SELECT actor.id FROM actor
            WHERE (EXISTS(SELECT 1 FROM direct_admin) OR (:assignedAdmin AND EXISTS(SELECT 1 FROM assigned)))
        )
        """;
    private static final String READER = ADMIN+"""
        , granted_reader AS MATERIALIZED (
            SELECT permission.user_id,permission.can_create_task,permission.can_create_project
              FROM shared_work_grants permission JOIN actor ON actor.id=permission.user_id
             WHERE permission.can_read FOR SHARE OF permission
        ), direct_tasks AS MATERIALIZED (
            SELECT authority.user_id FROM user_authorities authority JOIN actor ON actor.id=authority.user_id
             WHERE authority.authority='PAGE_TASKS' FOR SHARE OF authority
        ), reader AS MATERIALIZED (
            SELECT actor.id,EXISTS(SELECT 1 FROM administrator) AS system_admin,
                   (EXISTS(SELECT 1 FROM administrator) OR COALESCE(permission.can_create_task,FALSE)) AS can_create_task,
                   (EXISTS(SELECT 1 FROM administrator) OR COALESCE(permission.can_create_project,FALSE)) AS can_create_project
              FROM actor LEFT JOIN granted_reader permission ON permission.user_id=actor.id
             WHERE EXISTS(SELECT 1 FROM administrator) OR (permission.user_id IS NOT NULL
                AND (EXISTS(SELECT 1 FROM direct_tasks) OR (:assignedTasks AND EXISTS(SELECT 1 FROM assigned))))
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
        mutationGate();
        positive(targetId);
        if(grant==null||(!grant.canRead()&&(grant.canCreateTask()||grant.canCreateProject())))throw badRequest();
        var before=jdbc.queryForList("SELECT can_read,can_create_task,can_create_project FROM shared_work_grants WHERE user_id=?",targetId);
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
        var after=jdbc.queryForList("SELECT can_read,can_create_task,can_create_project FROM shared_work_grants WHERE user_id=?",targetId);
        if(!before.equals(after))audit("GRANT",targetId,"GRANT_SET",authentication,targetId,before,after);
    }

    public void require(Kind kind,long id,Access access,Authentication authentication) {
        lock(kind,id,access,authentication);
    }

    /** Trusted creation writer only: owner FK must already be written from the authenticated numeric actor in the same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerCreated(Kind kind,long id,Authentication authentication) {
        mutationGate();
        positive(id);var args=actorArgs(authentication).addValue("id",id,Types.BIGINT);
        String query=READER+"""
            , owned AS MATERIALIZED (
                SELECT root.id,root.shared_owner_user_id FROM %s root JOIN reader ON root.shared_owner_user_id=reader.id
                WHERE root.id=:id AND reader.%s FOR UPDATE OF root
            ), changed AS (
                INSERT INTO %s(%s,user_id,access_role) SELECT id,shared_owner_user_id,'OWNER' FROM owned
                ON CONFLICT(%s,user_id) DO NOTHING RETURNING user_id
            )
            SELECT (EXISTS(SELECT 1 FROM changed) OR EXISTS(
                SELECT 1 FROM owned JOIN %s member ON member.%s=owned.id
                WHERE member.user_id=owned.shared_owner_user_id AND member.access_role='OWNER')) authorized,EXISTS(SELECT 1 FROM changed) inserted
            """;
        var result=sql.queryForMap(query.formatted(kind.roots,kind.create,kind.members,kind.key,kind.key,kind.members,kind.key),args);
        if(!Boolean.TRUE.equals(result.get("authorized")))throw missing();
        if(Boolean.TRUE.equals(result.get("inserted")))
            audit(kind.name(),id,"CREATED",authentication,GeneralTicketActor.from(authentication).userId(),java.util.Map.of(),java.util.Map.of("ownerUserId",Long.toString(GeneralTicketActor.from(authentication).userId())));
    }

    public List<String> visibleIds(Kind kind,Authentication authentication) {
        return sql.query(READER+"""
            , readable_members AS MATERIALIZED (
                SELECT member.%s AS root_id FROM %s member JOIN reader ON reader.id=member.user_id
                 JOIN %s root ON root.id=member.%s
                 WHERE root.shared_owner_user_id IS NOT NULL
                   AND (member.access_role<>'OWNER' OR member.user_id=root.shared_owner_user_id)
                 FOR SHARE OF member
            )
            SELECT root.id FROM %s root CROSS JOIN reader
             WHERE reader.system_admin OR EXISTS(SELECT 1 FROM readable_members member WHERE member.root_id=root.id)
             ORDER BY root.id
            """.formatted(kind.key,kind.members,kind.roots,kind.key,kind.roots),actorArgs(authentication),(rs,row)->rs.getString("id"));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void lockBoardScope(String scopeKey) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",(rs,row)->true,"shared-work-board:"+scopeKey);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public long nextTaskSequence() {
        return jdbc.queryForObject("INSERT INTO task_seq(id,val) VALUES(1,1) ON CONFLICT(id) DO UPDATE SET val=task_seq.val+1 RETURNING val",Long.class);
    }
    public static Authentication current() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    }
    public boolean isSystemAdministrator(Authentication authentication) {
        if(GeneralTicketActor.from(authentication)==null)return false;
        return Boolean.TRUE.equals(sql.queryForObject(ADMIN+"SELECT EXISTS(SELECT 1 FROM administrator)",adminArgs(authentication),Boolean.class));
    }
    public long currentUserId() {
        var rows=sql.query(ADMIN+"SELECT id FROM actor",actorArgs(current()),(rs,row)->rs.getLong(1));
        if(rows.size()!=1)throw missing();return rows.get(0);
    }
    public long requireCreation(Kind kind,Authentication authentication) {
        mutationGate();
        var rows=sql.query(READER+"SELECT id FROM reader WHERE "+kind.create,actorArgs(authentication),(rs,row)->rs.getLong(1));
        if(rows.size()!=1)throw missing();return rows.get(0);
    }
    public void requireReader() {
        if(sql.query(READER+"SELECT id FROM reader",actorArgs(current()),(rs,row)->rs.getLong(1)).size()!=1)throw missing();
    }
    public boolean canRead(Kind kind,long id,Authentication authentication) {
        try{require(kind,id,Access.READ,authentication);return true;}
        catch(ResponseStatusException denied){if(denied.getStatusCode().value()==404)return false;throw denied;}
    }
    /** Only trusted SQL column names and numeric database IDs; used before search/count/LIMIT in legacy projections. */
    public String taskPredicate(String column) {
        if(!column.matches("[A-Za-z_][A-Za-z0-9_.]*"))throw new IllegalArgumentException("Invalid task column");
        if(GeneralTicketActor.from(current())==null)return "FALSE";
        var ids=visibleIds(Kind.TASK,current());
        return ids.isEmpty()?"FALSE":column+" IN ("+String.join(",",ids)+")";
    }
    public List<Long> visibleLongIds(Kind kind) {
        return visibleIds(kind,current()).stream().map(Long::valueOf).toList();
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
        requireEligibleTarget(targetId);
        var args=new MapSqlParameterSource("id",id).addValue("targetId",targetId).addValue("role",role.name());
        var previous=jdbc.queryForList("SELECT access_role FROM "+kind.members+" WHERE "+kind.key+"=? AND user_id=?",id,targetId);
        int changed=sql.update("INSERT INTO "+kind.members+"("+kind.key+",user_id,access_role) VALUES(:id,:targetId,:role) ON CONFLICT("+kind.key+",user_id) DO UPDATE SET access_role=EXCLUDED.access_role,updated_at=CURRENT_TIMESTAMP",args);
        if(changed!=1)throw missing();
        if(previous.isEmpty()||!role.name().equals(previous.get(0).get("access_role")))audit(kind.name(),id,"MEMBER_SET",authentication,targetId,previous,java.util.Map.of("role",role.name()));
    }

    public void removeMember(Kind kind,long id,long targetId,Authentication authentication) {
        positive(targetId);long owner=lock(kind,id,Access.OWNER,authentication);
        if(targetId==owner)throw new ResponseStatusException(HttpStatus.CONFLICT,"Нельзя удалить владельца");
        var previous=jdbc.queryForList("SELECT access_role FROM "+kind.members+" WHERE "+kind.key+"=? AND user_id=?",id,targetId);
        int changed=sql.update("DELETE FROM "+kind.members+" WHERE "+kind.key+"=:id AND user_id=:targetId",
            new MapSqlParameterSource("id",id).addValue("targetId",targetId));
        if(changed!=0)audit(kind.name(),id,"MEMBER_REMOVED",authentication,targetId,previous,java.util.Map.of());
    }

    private long lock(Kind kind,long id,Access access,Authentication authentication) {
        return lockStored(kind,id,access,actorArgs(authentication));
    }
    private long lockStored(Kind kind,long id,Access access,MapSqlParameterSource args) {
        if(access!=Access.READ)mutationGate();
        positive(id);args.addValue("id",id);
        String roles=switch(access){case READ->"'OWNER','EDITOR','VIEWER'";case WRITE->"'OWNER','EDITOR'";case OWNER->"'OWNER'";};
        String rootLock=access==Access.READ?"":" FOR UPDATE OF root";
        var owners=sql.query(READER+"""
            , owned AS MATERIALIZED (
                SELECT root.id,root.shared_owner_user_id,reader.id actor_id,reader.system_admin FROM %s root CROSS JOIN reader
                 WHERE root.id=:id AND (reader.system_admin OR root.shared_owner_user_id IS NOT NULL)%s
            ), membership AS MATERIALIZED (
                SELECT member.user_id FROM %s member JOIN owned ON member.%s=owned.id
                 WHERE member.user_id=owned.actor_id AND member.access_role IN (%s)
                   AND (member.access_role<>'OWNER' OR member.user_id=owned.shared_owner_user_id)
                 FOR SHARE OF member
            )
            SELECT COALESCE(shared_owner_user_id,0) FROM owned
             WHERE system_admin OR EXISTS(SELECT 1 FROM membership)
            """.formatted(kind.roots,rootLock,kind.members,kind.key,roles),args,(rs,row)->rs.getLong(1));
        if(owners.size()!=1)throw missing();
        return owners.get(0);
    }

    public void transferOwner(Kind kind,long id,long targetId,Authentication authentication) {
        positive(targetId);long previous=lock(kind,id,Access.OWNER,authentication);requireEligibleTarget(targetId);
        var args=new MapSqlParameterSource("id",id).addValue("target",targetId);
        sql.update("UPDATE "+kind.members+" SET access_role='EDITOR',updated_at=CURRENT_TIMESTAMP WHERE "+kind.key+"=:id AND access_role='OWNER'",args);
        sql.update("INSERT INTO "+kind.members+"("+kind.key+",user_id,access_role) VALUES(:id,:target,'OWNER') ON CONFLICT("+kind.key+",user_id) DO UPDATE SET access_role='OWNER',updated_at=CURRENT_TIMESTAMP",args);
        sql.update("UPDATE "+kind.roots+" SET shared_owner_user_id=:target WHERE id=:id",args);
        if(previous!=targetId)audit(kind.name(),id,"OWNER_TRANSFERRED",authentication,targetId,
            java.util.Map.of("ownerUserId",Long.toString(previous)),java.util.Map.of("ownerUserId",Long.toString(targetId)));
    }
    public Person eligibleReader(String username) {
        if(username==null)return null;
        var people=jdbc.query("SELECT account.id,account.username FROM users account WHERE account.username=? AND "+ACCOUNT,
            (rs,row)->new Person(rs.getString(1),rs.getString(2)),username);
        if(people.size()!=1)return null;
        try{requireEligibleTarget(Long.parseLong(people.get(0).userId()));return people.get(0);}
        catch(ResponseStatusException denied){if(denied.getStatusCode().value()==404)return null;throw denied;}
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireStored(Kind kind,long id,Access access,long userId,String username) {
        var actor=GeneralTicketActor.stored(userId,username);
        lockStored(kind,id,access,storedArgs(actor,PanelRolePermissionSnapshot.capture(jdbc,userId,username)));
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean deliverIfReadable(Kind kind,long id,long userId,String username,Runnable delivery) {
        try{requireStored(kind,id,Access.READ,userId,username);}
        catch(ResponseStatusException denied){if(denied.getStatusCode().value()==404)return false;throw denied;}
        delivery.run();return true;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void registerSystemCreated(long id,long ownerId,String ownerName) {
        mutationGate();requireEligibleTarget(ownerId);
        var owners=jdbc.query("SELECT id FROM tasks WHERE id=? AND shared_owner_user_id=? FOR UPDATE",(rs,row)->rs.getLong(1),id,ownerId);
        if(owners.size()!=1)throw missing();
        int inserted=jdbc.update("INSERT INTO shared_task_members(task_id,user_id,access_role) VALUES(?,?,'OWNER') ON CONFLICT DO NOTHING",id,ownerId);
        if(inserted==1)audit("TASK",id,"SYSTEM_CREATED",null,"auto_close",ownerId,java.util.Map.of(),java.util.Map.of("ownerUserId",Long.toString(ownerId)));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void registerSystemParticipant(long taskId,long userId) {
        mutationGate();requireEligibleTarget(userId);
        jdbc.update("INSERT INTO shared_task_members(task_id,user_id,access_role) VALUES(?,?,'EDITOR') ON CONFLICT DO NOTHING",taskId,userId);
        audit("TASK",taskId,"MEMBER_SET",null,"auto_close",userId,java.util.Map.of(),java.util.Map.of("role","EDITOR"));
    }

    public record AuditEvent(String id,String kind,String objectId,String action,String actorUserId,String actorName,
                             String targetUserId,String beforeState,String afterState,String occurredAt) {}
    public List<AuditEvent> history(Kind kind,Long id,long before,Authentication authentication) {
        if(before<=0)throw badRequest();
        if(kind==null){if(!isSystemAdministrator(authentication))throw administratorDenied();}
        else lock(kind,id,Access.OWNER,authentication);
        return jdbc.query("SELECT * FROM shared_work_access_audit WHERE id<?"+
            (kind==null?"":" AND kind=? AND object_id=?")+" ORDER BY id DESC LIMIT 50",
            (rs,row)->new AuditEvent(rs.getString("id"),rs.getString("kind"),rs.getString("object_id"),rs.getString("action"),
                rs.getString("actor_user_id"),rs.getString("actor_name"),rs.getString("target_user_id"),
                rs.getString("before_state"),rs.getString("after_state"),rs.getString("occurred_at")),
            kind==null?new Object[]{before}:new Object[]{before,kind.name(),id});
    }
    private void audit(String kind,long id,String action,Authentication authentication,Long target,
                       Object before,Object after) {
        var actor=GeneralTicketActor.from(authentication);
        audit(kind,id,action,actor.userId(),actor.username(),target,before,after);
    }
    private void audit(String kind,long id,String action,Long actorId,String actorName,Long target,Object before,Object after) {
        try{
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            jdbc.update("INSERT INTO shared_work_access_audit(kind,object_id,action,actor_user_id,actor_name,target_user_id,before_state,after_state) VALUES(?,?,?,?,?,?,?::jsonb,?::jsonb)",
                kind,id,action,actorId,actorName,target,mapper.writeValueAsString(before),mapper.writeValueAsString(after));
        }catch(com.fasterxml.jackson.core.JsonProcessingException failure){throw new IllegalStateException("Cannot serialize shared-work audit",failure);}
    }
    private void requireEligibleTarget(long id) {
        var names=jdbc.query("SELECT account.username FROM users account WHERE account.id=? AND "+ACCOUNT+" FOR SHARE OF account",(rs,row)->rs.getString(1),id);
        if(names.size()!=1)throw missing();
        var target=GeneralTicketActor.stored(id,names.get(0));
        var snapshot=PanelRolePermissionSnapshot.capture(jdbc,id,names.get(0));
        var args=storedArgs(target,snapshot);
        if(sql.query(READER+"SELECT id FROM reader",args,(rs,row)->rs.getLong(1)).size()!=1)throw missing();
    }
    public java.util.Set<String> readableRecipients(Kind kind,long id,java.util.Set<String> candidates) {
        var result=new java.util.LinkedHashSet<String>();
        for(String name:candidates) {
            var ids=jdbc.query("SELECT account.id FROM users account WHERE account.username=? AND "+ACCOUNT,(rs,row)->rs.getLong(1),name);
            if(ids.size()!=1)continue;
            var user=GeneralTicketActor.stored(ids.get(0),name);
            var args=storedArgs(user,PanelRolePermissionSnapshot.capture(jdbc,user.userId(),name)).addValue("id",id);
            boolean allowed=Boolean.TRUE.equals(sql.queryForObject(READER+"""
                SELECT EXISTS(SELECT 1 FROM reader CROSS JOIN %s root WHERE root.id=:id AND
                  (reader.system_admin OR (root.shared_owner_user_id IS NOT NULL AND EXISTS(
                    SELECT 1 FROM %s member WHERE member.%s=root.id AND member.user_id=reader.id
                    AND (member.access_role<>'OWNER' OR member.user_id=root.shared_owner_user_id)))))
                """.formatted(kind.roots,kind.members,kind.key),args,Boolean.class));
            if(allowed)result.add(name);
        }
        return result;
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void beginMutation() { mutationGate(); }
    private void mutationGate() {
        // Cross-object writes share one order; readers lock memberships rather than unrelated roots.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('shared-work-mutations',0))",(rs,row)->true);
    }
    private MapSqlParameterSource storedArgs(GeneralTicketActor actor,PanelRolePermissionSnapshot snapshot) {
        boolean tasks=snapshot.hasPage("PAGE_TASKS");
        return snapshot.bind(actor.bind(new MapSqlParameterSource())).addValue("assignedTasks",tasks);
    }
    private MapSqlParameterSource actorArgs(Authentication authentication) {
        var actor=GeneralTicketActor.from(authentication);
        if(actor==null||!actor.valid())throw missing();
        var snapshot=PanelRolePermissionSnapshot.capture(jdbc,actor.userId(),actor.username());
        boolean tasks=snapshot.hasPage("PAGE_TASKS");
        return snapshot.bind(actor.bind(new MapSqlParameterSource())).addValue("assignedTasks",tasks);
    }
    private MapSqlParameterSource adminArgs(Authentication authentication) {
        var actor=GeneralTicketActor.from(authentication);
        if(actor==null||!actor.valid())throw administratorDenied();
        var snapshot=PanelRolePermissionSnapshot.capture(jdbc,actor.userId(),actor.username());
        boolean tasks=snapshot.hasPage("PAGE_TASKS");
        return snapshot.bind(actor.bind(new MapSqlParameterSource())).addValue("assignedTasks",tasks);
    }
    private static void positive(long id){if(id<=0)throw badRequest();}
    private static ResponseStatusException badRequest(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Некорректные параметры доступа");}
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Объект недоступен");}
    private static AccessDeniedException administratorDenied(){return new AccessDeniedException("Требуется действующий системный ROLE_ADMIN");}
}
