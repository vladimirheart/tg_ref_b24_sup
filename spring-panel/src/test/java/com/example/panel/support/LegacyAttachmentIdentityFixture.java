package com.example.panel.support;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.*;
import com.example.panel.storage.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

/** Isolated SQL and a frozen constructor bridge, never a working datasource. */
public final class LegacyAttachmentIdentityFixture {
    private LegacyAttachmentIdentityFixture() { }
    public static final String TICKET="T",FILE="private.mp4",KEY="objects/"+FILE;
    public static final byte[] BYTES={0,1,2,3,4,5};
    public enum Route { DIRECT,KEY,PATH }
    public static void schema(JdbcTemplate jdbc,boolean existingUsers) {
        if(!existingUsers)jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT UNIQUE,password TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE)");
        jdbc.execute("""
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE chat_history(id BIGINT PRIMARY KEY,ticket_id TEXT);
            CREATE TABLE chat_attachment_metadata(chat_history_id BIGINT PRIMARY KEY,ticket_id TEXT,storage_key TEXT,
                storage_provider TEXT DEFAULT 'local_fs',storage_class TEXT DEFAULT 'dialog_attachment',
                normalization_status TEXT DEFAULT 'normalized',deleted_at TIMESTAMPTZ);
            """);
    }
    public static void seed(JdbcTemplate jdbc,String name,String password) {
        jdbc.execute("TRUNCATE users,businesses,business_memberships,business_access_grants,tickets,chat_history,chat_attachment_metadata");
        jdbc.update("INSERT INTO users(id,username,password) VALUES(7,?,?)",name,password);
        jdbc.update("INSERT INTO businesses(id) VALUES(11),(22)");
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,'VIEWER')");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,business_id) VALUES(101,?,11)",TICKET);
        jdbc.update("INSERT INTO chat_history(id,ticket_id) VALUES(9,?),(10,?)",TICKET,TICKET);
        jdbc.update("INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key) VALUES(9,?,?),(10,?,?)",TICKET,TICKET+"/"+FILE,TICKET,KEY);
    }
    public static Authentication actor(long id,String name) {
        var rights=AuthorityUtils.createAuthorityList("PAGE_DIALOGS");
        return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"unused",true,rights),null,rights);
    }
    public static AttachmentService service(JdbcTemplate jdbc,JdbcTemplate accessJdbc,MultibusinessFeatureFlags flags,
            AttachmentObjectStorageService storage,Path root)throws Exception {
        var tickets=new LegacyTicketIdJdbcGuard(jdbc);var owner=new TicketAttachmentOwnerJdbcGuard(jdbc,tickets);
        var constructor=AttachmentService.class.getConstructors()[0];
        var args=new ArrayList<Object>(List.of(new PermissionService(),storage,tickets,owner,root.resolve("files").toString(),root.resolve("kb").toString()));
        if(constructor.getParameterCount()==7) {
            var type=Class.forName("com.example.panel.storage.LegacyAttachmentReadAccess");
            args.add(type.getConstructor(JdbcTemplate.class,MultibusinessFeatureFlags.class).newInstance(accessJdbc,flags));
        }
        try { return (AttachmentService)constructor.newInstance(args.toArray()); }
        catch(InvocationTargetException ex) { throw new AssertionError(ex.getCause()); }
    }
    public static org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> download(
            AttachmentService service,Authentication authentication,Route route,String range)throws Exception {
        return switch(route) {
            case DIRECT->range==null?service.downloadTicketAttachment(authentication,TICKET,FILE):service.downloadTicketAttachment(authentication,TICKET,FILE,range);
            case KEY->range==null?service.downloadTicketAttachmentByStorageKey(authentication,KEY):service.downloadTicketAttachmentByStorageKey(authentication,KEY,range);
            case PATH->range==null?service.downloadTicketAttachmentByPath(authentication,"attachments/"+KEY):service.downloadTicketAttachmentByPath(authentication,"attachments/"+KEY,range);
        };
    }
    public static Map<String,String> snapshot(JdbcTemplate jdbc) {
        var result=new LinkedHashMap<String,String>();
        for(String table:List.of("tickets","chat_history","chat_attachment_metadata"))
            result.put(table,jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text)::text,'[]') FROM "+table+" r",String.class));
        return result;
    }
    public static void replace(JdbcTemplate jdbc,String change) {
        switch(change) {
            case "rename-reuse"->{jdbc.update("UPDATE users SET username='renamed' WHERE id=7");jdbc.update("INSERT INTO users(id,username) VALUES(55,'caller')");jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");}
            case "delete-reuse"->{jdbc.update("DELETE FROM users WHERE id=7");jdbc.update("INSERT INTO users(id,username) VALUES(55,'caller')");jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");}
            case "case-change"->jdbc.update("UPDATE users SET username='Caller' WHERE id=7");
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");
            case "membership"->jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=7");
            case "business"->jdbc.update("UPDATE tickets SET business_id=22");
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,business_id) VALUES(202,?,11)",TICKET);
            case "general"->jdbc.update("UPDATE tickets SET is_general=TRUE");
            default->throw new IllegalArgumentException(change);
        }
    }
}
