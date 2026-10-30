package com.example.panel.support;

import com.example.panel.service.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.IntConsumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

/** Dedicated BUSINESS child fixture; previous GENERAL fixture supplies only isolated tables/media bytes. */
public final class BusinessChildActorFixture {
    private BusinessChildActorFixture(){}
    public enum Surface { card,history,answers,workspace,spans,legacy,telegram,vk }
    public static final String PUBLIC=GeneralReadActorFixture.PUBLIC,RAW=GeneralReadActorFixture.RAW;
    public static final byte[] BYTES=GeneralReadActorFixture.BYTES;
    public static final String SHA=GeneralReadActorFixture.SHA;
    public static final List<String> EXTRA=List.of("businesses","business_memberships","business_access_grants","ticket_responsibles","ticket_spans");
    public static void schema(JdbcTemplate jdbc,boolean existingUsers){
        GeneralReadActorFixture.schema(jdbc,existingUsers);
        jdbc.execute("""
            ALTER TABLE tickets ADD COLUMN business_categories JSONB DEFAULT '["Delivery"]', ADD COLUMN resolved_at TIMESTAMPTZ, ADD COLUMN resolved_by TEXT;
            ALTER TABLE ticket_attributes ADD COLUMN input_type TEXT DEFAULT 'text';
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,code TEXT,display_name TEXT,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE ticket_responsibles(ticket_id TEXT,responsible TEXT);
            CREATE TABLE ticket_spans(ticket_id TEXT,id BIGINT,span_no INT,started_at TIMESTAMPTZ,ended_at TIMESTAMPTZ,duration_seconds INT);
            """);
    }
    public static void seed(JdbcTemplate jdbc,long id,String name,String password,boolean all){
        jdbc.execute("TRUNCATE "+String.join(",",EXTRA));GeneralReadActorFixture.seed(jdbc,id,name,password);
        jdbc.execute("INSERT INTO businesses VALUES(11,'one','Private business','active'),(22,'two','Foreign business','active')");
        jdbc.execute("UPDATE tickets SET is_general=FALSE,business_id=11,general_selection_source=NULL,business_selection_source='USER_SELECTION'");
        jdbc.execute("UPDATE chat_history SET attachment=replace(attachment,'/general/','/business-11/')");jdbc.execute("UPDATE chat_attachment_metadata SET storage_key=replace(storage_key,'/general/','/business-11/')");
        jdbc.update("INSERT INTO ticket_responsibles VALUES(?,'private responsible')",RAW);
        jdbc.update("INSERT INTO ticket_spans VALUES(?,1,1,'2020-01-01T00:00:00Z','2020-01-01T00:00:05Z',5)",RAW);
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'VIEWER')");
        if(all)jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','VIEWER')",id);
        else jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,11,'VIEWER')",id);
    }
    public static Authentication auth(long id,String name){return GeneralReadActorFixture.auth(id,name);}
    /** Initial fixture capture by exact ID only. It never repairs or grants an account. */
    public static Authentication stored(JdbcTemplate jdbc,Long id){
        if(id==null)return null;var names=jdbc.queryForList("SELECT username FROM users WHERE id=?",String.class,id);return auth(id,names.size()==1?names.get(0):"missing fixture account");
    }
    public static String publicId(Surface surface){return surface==Surface.vk?GeneralReadActorFixture.VK_PUBLIC:PUBLIC;}
    public static long historyId(Surface surface){return switch(surface){case legacy->1;case telegram->2;case vk->3;default->throw new IllegalArgumentException();};}
    public static String storageKey(Surface surface){return GeneralReadActorFixture.storageKey(switch(surface){case legacy->GeneralReadActorFixture.Surface.legacy;case telegram->GeneralReadActorFixture.Surface.telegram;case vk->GeneralReadActorFixture.Surface.vk;default->throw new IllegalArgumentException();}).replace("/general/","/business-11/");}
    public static String method(Surface surface){return switch(surface){case card->"findByPublicId";case history->"findHistoryByPublicId";case answers->"findAnswersByPublicId";case workspace->"findWorkspaceByPublicId";case spans->"findSpansByPublicId";default->"findAttachmentByPublicId";};}
    public static Optional<?> call(BusinessScopedTicketReadService service,Surface surface,Authentication actor){
        var methods=Arrays.stream(BusinessScopedTicketReadService.class.getMethods()).filter(m->m.getName().equals(method(surface))).toList();if(methods.size()!=1)throw new AssertionError("One public child read entry required");var method=methods.get(0);Object bound=actor;
        if(method.getParameterTypes()[0]==Long.class)bound=actor!=null&&actor.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p?p.getUserId():7L;
        Object[] args=switch(surface){case history->new Object[]{bound,PUBLIC,null,10};case card,answers,workspace,spans->new Object[]{bound,PUBLIC};default->new Object[]{bound,publicId(surface),historyId(surface)};};
        try{return (Optional<?>)method.invoke(service,args);}catch(InvocationTargetException ex){if(ex.getCause() instanceof RuntimeException cause)throw cause;throw new AssertionError(ex.getCause());}catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
    }
    public static void change(JdbcTemplate jdbc,String mode){
        if(mode.equals("scope")){jdbc.execute("DELETE FROM business_memberships WHERE user_id=7");jdbc.execute("DELETE FROM business_access_grants WHERE user_id=7");}
        else if(mode.equals("archive"))jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
        else if(mode.equals("null-blocked"))jdbc.update("UPDATE users SET is_blocked=NULL WHERE id=7");
        else GeneralReadActorFixture.change(jdbc,mode);
    }
    public static Map<String,List<String>> snapshot(JdbcTemplate jdbc){var result=GeneralReadActorFixture.snapshot(jdbc);for(String table:EXTRA)result.put(table,jdbc.queryForList("SELECT to_jsonb(row)::text FROM "+table+" row ORDER BY to_jsonb(row)::text",String.class));return result;}
    public static JdbcTemplate boundary(JdbcTemplate jdbc,IntConsumer before)throws Exception{return GeneralReadActorFixture.boundary(jdbc,before);}
}
