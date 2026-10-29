package com.example.panel.support;

import com.example.panel.security.PanelUserPrincipal;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Test-only login fixture and frozen old/new API bridge; never used by application code. */
public final class GeneralTicketActorTestSupport {
    private GeneralTicketActorTestSupport() {}
    public static Authentication auth(long id,String name) {
        return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"unused",true,List.of()),null,List.of());
    }
    public static Authentication auth(JdbcTemplate jdbc,String login) {
        if(login==null||login.isBlank())return null;
        try {
            var rows=jdbc.query("SELECT id,username FROM users WHERE lower(username)=lower(?)",(rs,n)->auth(rs.getLong("id"),rs.getString("username")),login);
            return rows.size()==1?rows.get(0):null;
        }catch(org.springframework.dao.DataAccessException e){return null;}
    }
    @SuppressWarnings("unchecked")
    public static <T>T call(Object service,String method,int actorIndex,Object... inputs) {
        var methods=Arrays.stream(service.getClass().getDeclaredMethods()).filter(m->m.getName().equals(method)&&m.getParameterCount()==inputs.length)
            .filter(m->actorIndex<0||m.getParameterTypes()[actorIndex]==String.class||m.getParameterTypes()[actorIndex]==Authentication.class).toList();
        if(methods.size()!=1)throw new AssertionError("Exactly one actor entry API: "+method);
        var entry=methods.get(0);var args=inputs.clone();
        if(actorIndex>=0&&entry.getParameterTypes()[actorIndex]==String.class)args[actorIndex]=args[actorIndex]==null?null:((Authentication)args[actorIndex]).getName();
        entry.setAccessible(true);
        try{return (T)entry.invoke(service,args);}
        catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException failure)throw failure;throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    @SuppressWarnings("unchecked")
    public static void register(Object service,Authentication actor,SseEmitter emitter) {
        Object key=actor.getName().toLowerCase(Locale.ROOT);
        try{
            var type=Class.forName("com.example.panel.service.GeneralTicketActor");var bind=type.getDeclaredMethod("from",Authentication.class);
            bind.setAccessible(true);key=bind.invoke(null,actor);
        }catch(ClassNotFoundException ignored){/* Unchanged product baseline retains name keys. */}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
        var map=(ConcurrentHashMap<Object,CopyOnWriteArraySet<SseEmitter>>)ReflectionTestUtils.getField(service,"emittersByUser");
        map.computeIfAbsent(key,k->new CopyOnWriteArraySet<>()).add(emitter);
    }
}
