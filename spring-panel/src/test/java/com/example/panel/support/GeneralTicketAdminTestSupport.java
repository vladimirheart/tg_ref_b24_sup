package com.example.panel.support;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.GeneralTicketAccessService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

/** Test-only administrator login fixture and frozen bridge across the admin API revision. */
public final class GeneralTicketAdminTestSupport {
    private GeneralTicketAdminTestSupport() {}
    public enum Operation { accounts,grantRead,revokeRead,grantOperate,revokeOperate }
    public static Authentication bound(long id,String name) {
        var rights=List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),new SimpleGrantedAuthority("PAGE_SETTINGS"));
        return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"unused",true,rights),null,rights);
    }
    public static List<String> roleSources(){return List.of("role","wildcard","trim-unicode","direct-admin","direct-page","direct-only-invalid-json","missing-role","portal","settings-whitespace","malformed","no-settings","non-admin","non-list");}
    public static boolean roleAllows(String source){return Set.of("role","wildcard","trim-unicode","direct-admin","direct-page","direct-only-invalid-json").contains(source);}
    public static void assignRole(JdbcTemplate jdbc,long userId,String source){
        String name=switch(source){case "trim-unicode"->"\u0001AdMİN ";case "portal"->"portal_admin";case "non-admin","direct-admin"->"operator";case "wildcard"->"ADMIN";default->"admin";};
        String permissions=switch(source){case "wildcard","portal"->"{\"pages\":[\"*\"]}";case "settings-whitespace"->"{\"pages\":[\" settings \"]}";case "malformed","direct-only-invalid-json"->"{";case "no-settings","direct-page"->"{\"pages\":[]}";case "non-list"->"{\"pages\":\"settings\"}";default->"{\"pages\":[\"settings\"]}";};
        jdbc.update("INSERT INTO roles(id,name,permissions) OVERRIDING SYSTEM VALUE VALUES(100,?,?),(200,'operator','{}') ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,permissions=EXCLUDED.permissions",name,permissions);
        jdbc.update("UPDATE users SET role_id=? WHERE id=?",source.equals("missing-role")?999:100,userId);
        jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority IN ('ROLE_ADMIN','PAGE_SETTINGS')",userId);
        if(source.equals("direct-admin")||source.equals("direct-only-invalid-json"))jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,'ROLE_ADMIN')",userId);
        if(source.equals("direct-page")||source.equals("direct-only-invalid-json"))jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,'PAGE_SETTINGS')",userId);
    }
    public static void changeAssignedRole(JdbcTemplate jdbc,long userId,String mutation){switch(mutation){case "name"->jdbc.update("UPDATE roles SET name='operator' WHERE id=100");case "page"->jdbc.update("UPDATE roles SET permissions='{\"pages\":[]}' WHERE id=100");case "pointer"->jdbc.update("UPDATE users SET role_id=200 WHERE id=?",userId);default->throw new AssertionError(mutation);}}
    /** A separate administrator without GENERAL grants; never promotes the tested recipient. */
    public static Authentication administrator(JdbcTemplate jdbc) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='users' AND column_name='role_id')",Boolean.class)))jdbc.execute("ALTER TABLE users ADD COLUMN role_id BIGINT");
        jdbc.execute("CREATE TABLE IF NOT EXISTS roles(id BIGINT PRIMARY KEY,name TEXT,permissions TEXT)");
        long id=9007199254740000L;String name="fixture_general_admin";
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND username=?)",Boolean.class,id,name)))return bound(id,name);
        boolean password=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='users' AND column_name='password')",Boolean.class));
        jdbc.update("INSERT INTO users(id,username,enabled,is_blocked"+(password?",password":"")+") OVERRIDING SYSTEM VALUE VALUES(?,?,TRUE,FALSE"+(password?",'unused'":"")+") ON CONFLICT(id) DO NOTHING",id,name);
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_authorities(user_id BIGINT,authority VARCHAR(100),PRIMARY KEY(user_id,authority))");
        for(String right:List.of("ROLE_ADMIN","PAGE_SETTINGS"))jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,?) ON CONFLICT(user_id,authority) DO NOTHING",id,right);
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id=?",String.class,id)).isEqualTo(name);
        return bound(id,name);
    }
    private static Method entry(Operation operation) {
        var methods=Arrays.stream(GeneralTicketAccessService.class.getMethods()).filter(m->m.getDeclaringClass()==GeneralTicketAccessService.class&&m.getName().equals(operation.name())).toList();
        if(methods.size()!=1)throw new AssertionError("Exactly one administrator entry: "+operation);
        return methods.get(0);
    }
    public static Object call(GeneralTicketAccessService service,Operation operation,Authentication actor,long target) {
        var method=entry(operation);boolean authenticated=method.getParameterCount()>0&&method.getParameterTypes()[0]==Authentication.class;
        Object[] args=operation==Operation.accounts?(authenticated?new Object[]{actor}:new Object[]{}):(authenticated?new Object[]{actor,target}:new Object[]{target});
        try{return method.invoke(service,args);}
        catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException failure)throw failure;throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    public static void beforeCall(GeneralTicketAccessService service,Operation operation,long target,Runnable mutation)throws Exception {
        var proxy=doAnswer(invocation->{mutation.run();return invocation.callRealMethod();}).when(service);var method=entry(operation);
        boolean authenticated=method.getParameterCount()>0&&method.getParameterTypes()[0]==Authentication.class;
        if(operation==Operation.accounts){if(authenticated)method.invoke(proxy,any(Authentication.class));else method.invoke(proxy);}
        else if(authenticated)method.invoke(proxy,any(Authentication.class),eq(target));else method.invoke(proxy,eq(target));
    }
}
