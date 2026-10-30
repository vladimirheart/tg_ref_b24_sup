package com.example.panel.support;

import com.example.panel.service.*;
import java.lang.reflect.*;
import java.sql.Connection;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/** Frozen bridge exercises the same public entry on the previous and current product. */
public final class BusinessCatalogActorFixture {
    private BusinessCatalogActorFixture() {}
    public enum Surface { catalog,list,page,export,summary,categories,manual }
    public static void schema(JdbcTemplate jdbc,boolean existingUsers) {
        BusinessChildActorFixture.schema(jdbc,existingUsers);
        jdbc.execute("""
            ALTER TABLE messages ADD COLUMN location_type TEXT, ADD COLUMN city TEXT, ADD COLUMN location_name TEXT,
                ADD COLUMN created_at TIMESTAMPTZ, ADD COLUMN created_date TEXT, ADD COLUMN created_time TEXT;
            ALTER TABLE tickets ADD COLUMN reopen_count INT, ADD COLUMN closed_count INT,
                ADD COLUMN work_time_total_sec BIGINT, ADD COLUMN last_reopen_at TIMESTAMPTZ;
            CREATE SEQUENCE manual_client_user_id_seq START WITH -1 INCREMENT BY -1;
            CREATE TABLE manual_business_clients(user_id BIGINT PRIMARY KEY,business_id BIGINT,display_name TEXT,created_by_user_id BIGINT);
            CREATE TABLE bot_users(user_id BIGINT PRIMARY KEY);
            CREATE TABLE business_locations(id BIGINT,business_id BIGINT,status TEXT,location_type TEXT,city TEXT,location_name TEXT);
            """);
    }
    public static void seed(JdbcTemplate jdbc,long id,String name,String password,boolean all) {
        jdbc.execute("TRUNCATE manual_business_clients,bot_users,business_locations");
        BusinessChildActorFixture.seed(jdbc,id,name,password,all);
        jdbc.execute("UPDATE business_memberships SET role_code='OPERATOR'; UPDATE business_access_grants SET role_code='OPERATOR'");
    }
    public static Map<String,List<String>> snapshot(JdbcTemplate jdbc) {
        var result=BusinessChildActorFixture.snapshot(jdbc);
        for(String table:List.of("manual_business_clients","bot_users","business_locations"))
            result.put(table,jdbc.queryForList("SELECT to_jsonb(row)::text FROM "+table+" row ORDER BY to_jsonb(row)::text",String.class));
        return result;
    }
    public static void change(JdbcTemplate jdbc,String mode) {
        if(mode.equals("viewer"))jdbc.execute("UPDATE business_memberships SET role_code='VIEWER'; UPDATE business_access_grants SET role_code='VIEWER'");
        else BusinessChildActorFixture.change(jdbc,mode);
    }
    public static Object call(BusinessScopedTicketReadService read,ManualBusinessTicketCreationService create,Surface surface,Authentication actor) {
        String name=switch(surface){case catalog->"availableBusinesses";case list->"listTickets";case page->"pageTickets";case export->"exportTickets";case summary->"summarizeTickets";case categories->"summarizeCategories";case manual->"create";};
        Class<?> type=surface==Surface.manual?ManualBusinessTicketCreationService.class:BusinessScopedTicketReadService.class;
        var method=Arrays.stream(type.getMethods()).filter(m->m.getName().equals(name)).max(Comparator.comparingInt(Method::getParameterCount)).orElseThrow();
        Object bound=actor;
        if(method.getParameterTypes()[0]==Long.class||method.getParameterTypes()[0]==long.class)
            bound=actor!=null&&actor.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p?p.getUserId():7L;
        var context=BusinessAccessService.AllAccessibleBusinesses.INSTANCE;
        Object[] args=switch(surface){
            case catalog->new Object[]{bound};case summary,categories->new Object[]{bound,context};
            case list,page->new Object[]{bound,context,10,0,null,null,null,null};
            case export->new Object[]{bound,context,null,null,null,null};
            case manual->new Object[]{bound,11L,"New client","New problem",null};
        };
        try{return method.invoke(surface==Surface.manual?create:read,args);}
        catch(InvocationTargetException ex){if(ex.getCause() instanceof RuntimeException cause)throw cause;throw new AssertionError(ex.getCause());}
        catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
    }
    public static long size(Object value) {
        if(value instanceof List<?> list)return list.size();
        if(value instanceof BusinessScopedTicketReadService.TicketPage page)return page.tickets().size();
        if(value instanceof BusinessScopedTicketReadService.TicketSummary summary)return summary.total();
        if(value instanceof ManualBusinessTicketCreationService.CreatedTicket)return 1;
        throw new AssertionError(value);
    }
    /** Fires only at the owning SELECT/INSERT, after the preliminary business checks. */
    public static JdbcTemplate boundary(JdbcTemplate jdbc,Runnable before)throws Exception {
        Connection actual=jdbc.getDataSource().getConnection();
        var proxy=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,args)->{
            if(m.getName().equals("prepareStatement")&&args!=null&&args[0] instanceof String sql
                    &&(sql.stripLeading().startsWith("SELECT business.id")||sql.contains("FROM tickets ticket")||sql.contains("INSERT INTO manual_business_clients")))before.run();
            try{return m.invoke(actual,args);}catch(InvocationTargetException ex){throw ex.getCause();}
        });
        return new JdbcTemplate(new SingleConnectionDataSource(proxy,true));
    }
    public static boolean denied(BusinessScopedTicketReadService read,ManualBusinessTicketCreationService create,Surface surface,Authentication actor) {
        try{return size(call(read,create,surface,actor))==0;}catch(AccessDeniedException ex){return true;}
    }
}
