package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import java.lang.reflect.*;
import java.sql.Connection;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Frozen SQL/SSE bridge; captures fixture identity once and never repairs an account or grant. */
public final class UiEventActorFixture {
    private UiEventActorFixture() {}
    public static final String RAW=" R233/Ю ?%#+ ",PUBLIC="a".repeat(32);
    public static final List<String> TABLES=List.of("users","businesses","business_memberships","business_access_grants","tickets");
    public static void schema(JdbcTemplate jdbc,boolean existingUsers) {
        if(!existingUsers)jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,password TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE)");
        jdbc.execute("""
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT);
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN,PRIMARY KEY(user_id,ticket_id));
            """);
    }
    public static void seed(JdbcTemplate jdbc,long id,String name,String password,boolean all) {
        jdbc.execute("TRUNCATE "+String.join(",",TABLES));
        jdbc.update("INSERT INTO users(id,username,password) VALUES(?,?,?),(55,'other',?),(66,'foreign',?)",id,name,password,password,password);
        jdbc.execute("INSERT INTO businesses VALUES(11,'active'),(22,'active')");
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'VIEWER'),(66,22,'VIEWER')");
        if(all)jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','VIEWER')",id);
        else jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,11,'VIEWER')",id);
        jdbc.update("INSERT INTO tickets VALUES(101,?,?,11,FALSE),(102,'foreign',?,22,FALSE),(103,'compat',?,NULL,FALSE),(104,'general',?,NULL,TRUE)",RAW,PUBLIC,"b".repeat(32),"c".repeat(32),"d".repeat(32));
    }
    public static Authentication auth(long id,String name){return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"unused",true,List.of()),null,List.of());}
    public static Authentication stored(JdbcTemplate jdbc,String name) {
        if(name==null)return null;
        var rows=jdbc.query("SELECT id,username FROM users WHERE lower(username)=lower(?)",(rs,n)->auth(rs.getLong("id"),rs.getString("username")),name);
        return rows.size()==1?rows.get(0):auth(9000,name);
    }
    public static UiEventTicketScope scope(boolean compatibility){return compatibility?new UiEventTicketScope(103L,"compat","c".repeat(32),"UNRESOLVED",null):new UiEventTicketScope(101L,RAW,PUBLIC,"BUSINESS",11L);}
    static Object invoke(Object service,String name,Authentication actor,Object... tail) {
        var methods=Arrays.stream(service.getClass().getMethods()).filter(m->m.getName().equals(name)).toList();if(methods.size()!=1)throw new AssertionError("One public actor entry: "+name);var method=methods.get(0);Object[] args=new Object[tail.length+1];
        args[0]=method.getParameterTypes()[0]==String.class?actor!=null&&actor.getPrincipal() instanceof PanelUserPrincipal p?p.getUsername():"caller":actor;System.arraycopy(tail,0,args,1,tail.length);
        try{return method.invoke(service,args);}catch(InvocationTargetException ex){if(ex.getCause() instanceof RuntimeException cause)throw cause;throw new AssertionError(ex.getCause());}catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
    }
    public static boolean canRead(UiEventTicketAccessService access,Authentication actor,UiEventTicketScope scope){return (Boolean)invoke(access,"canReadTicket",actor,scope);}
    public static SseEmitter connect(UiEventStreamService stream,Authentication actor){var emitter=(SseEmitter)invoke(stream,"connect",actor);clear(emitter);return emitter;}
    public static Object key(Authentication actor) {
        try{var type=Class.forName("com.example.panel.service.UiEventRecipient");var from=type.getDeclaredMethod("from",Authentication.class);from.setAccessible(true);return from.invoke(null,actor);}
        catch(ClassNotFoundException ex){return ((PanelUserPrincipal)actor.getPrincipal()).getUsername().toLowerCase(Locale.ROOT);}
        catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
    }
    @SuppressWarnings("unchecked") public static void register(UiEventStreamService stream,Authentication actor,SseEmitter emitter){var entries=(Map<Object,java.util.concurrent.CopyOnWriteArraySet<SseEmitter>>)ReflectionTestUtils.getField(stream,"emittersByUser");entries.computeIfAbsent(key(actor),k->new java.util.concurrent.CopyOnWriteArraySet<>()).add(emitter);}
    public static void clear(SseEmitter emitter){((Collection<?>)ReflectionTestUtils.getField(emitter,"earlySendAttempts")).clear();}
    @SuppressWarnings("unchecked") public static List<Map<String,Object>> payloads(SseEmitter emitter){return ((Collection<?>)ReflectionTestUtils.getField(emitter,"earlySendAttempts")).stream().map(x->ReflectionTestUtils.getField(x,"data")).filter(Map.class::isInstance).map(x->(Map<String,Object>)x).toList();}
    public static void change(JdbcTemplate jdbc,String mode){switch(mode){
        case "rename-reuse"->{jdbc.execute("UPDATE users SET username='retired' WHERE id=7");jdbc.execute("UPDATE users SET username='caller' WHERE id=55");}
        case "delete-reuse"->{jdbc.execute("DELETE FROM users WHERE id=7");jdbc.execute("UPDATE users SET username='caller' WHERE id=55");}
        case "case"->jdbc.execute("UPDATE users SET username='CALLER' WHERE id=7");
        case "disabled"->jdbc.execute("UPDATE users SET enabled=FALSE WHERE id=7");
        case "blocked"->jdbc.execute("UPDATE users SET is_blocked=TRUE WHERE id=7");
        case "null-blocked"->jdbc.execute("UPDATE users SET is_blocked=NULL WHERE id=7");
        case "duplicate"->jdbc.execute("UPDATE users SET username='CALLER',enabled=FALSE WHERE id=55");
        default->throw new IllegalArgumentException(mode);
    }}
    public static Map<String,List<String>> snapshot(JdbcTemplate jdbc){var result=new LinkedHashMap<String,List<String>>();for(String table:TABLES)result.put(table,jdbc.queryForList("SELECT to_jsonb(row)::text FROM "+table+" row ORDER BY to_jsonb(row)::text",String.class));return result;}
    public static JdbcTemplate boundary(JdbcTemplate jdbc,Runnable before)throws Exception {
        Connection actual=jdbc.getDataSource().getConnection();var proxy=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,args)->{if(m.getName().equals("prepareStatement")&&args!=null&&args[0] instanceof String sql&&sql.contains("FROM tickets owner"))before.run();try{return m.invoke(actual,args);}catch(InvocationTargetException ex){throw ex.getCause();}});return new JdbcTemplate(new SingleConnectionDataSource(proxy,true));
    }
}
