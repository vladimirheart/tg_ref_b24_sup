package com.example.panel.support;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.GeneralTicketAccessService;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Isolated GENERAL entry fixture. The bridge runs identical tests against both API revisions. */
public final class GeneralEntryIdentityFixture {
    private GeneralEntryIdentityFixture() {}

    public static void schema(JdbcTemplate jdbc, boolean existingUsers) {
        if (!existingUsers) jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,password TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE)");
        for (String table : List.of("general_ticket_read_grants", "general_ticket_operate_grants")) {
            jdbc.execute("CREATE TABLE " + table + "(user_id BIGINT PRIMARY KEY,is_active BOOLEAN DEFAULT TRUE,updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    public static void seed(JdbcTemplate jdbc, long id, String name, String password) {
        jdbc.execute("TRUNCATE users,general_ticket_read_grants,general_ticket_operate_grants");
        jdbc.update("INSERT INTO users(id,username,password) VALUES(?,?,?),(55,'target','unused')", id, name, password);
        jdbc.update("INSERT INTO general_ticket_read_grants(user_id) VALUES(?),(55)", id);
        jdbc.update("INSERT INTO general_ticket_operate_grants(user_id) VALUES(?),(55)", id);
    }

    public static Authentication auth(long id, String name) {
        return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"unused",true,List.of()),null,List.of());
    }

    public static long resolve(GeneralTicketAccessService access, Authentication actor) {
        var methods = Arrays.stream(GeneralTicketAccessService.class.getMethods())
            .filter(m -> m.getName().equals("requireAuthenticatedUserId")).toList();
        if (methods.size() != 1) throw new AssertionError("Exactly one identity entry API is required");
        var method = methods.get(0);
        Object argument = method.getParameterTypes()[0] == String.class ? (actor == null ? null : actor.getName()) : actor;
        try { return ((Number) method.invoke(access, argument)).longValue(); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    public static void change(JdbcTemplate jdbc, String kind) {
        switch (kind) {
            case "rename-reuse", "delete-reuse" -> {
                if (kind.equals("rename-reuse")) jdbc.update("UPDATE users SET username='renamed' WHERE id=7");
                else jdbc.update("DELETE FROM users WHERE id=7");
                jdbc.update("INSERT INTO users(id,username,password) VALUES(99,'caller','unused')");
                jdbc.update("INSERT INTO general_ticket_read_grants(user_id) VALUES(99)");
                jdbc.update("INSERT INTO general_ticket_operate_grants(user_id) VALUES(99)");
            }
            case "case-change" -> jdbc.update("UPDATE users SET username='CALLER' WHERE id=7");
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");
            case "ambiguous" -> jdbc.update("INSERT INTO users(id,username,password,enabled) VALUES(99,'CALLER','unused',FALSE)");
            default -> throw new IllegalArgumentException(kind);
        }
    }

    public static Map<String,List<Map<String,Object>>> grants(JdbcTemplate jdbc) {
        var result = new LinkedHashMap<String,List<Map<String,Object>>>();
        for (String table : List.of("general_ticket_read_grants","general_ticket_operate_grants")) {
            result.put(table,jdbc.queryForList("SELECT * FROM " + table + " ORDER BY user_id"));
        }
        return result;
    }
}
