package com.example.panel.support;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.*;
import java.lang.reflect.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Dedicated SQL fixture and an API bridge for running frozen cases on the previous revision. */
public final class GeneralReadActorFixture {
    private GeneralReadActorFixture(){}
    public enum Surface { page,find,history,answers,legacy,telegram,vk,document }
    public static final String PUBLIC="a".repeat(32),VK_PUBLIC="b".repeat(32),RAW="opaque-Ю?%#+",CATALOG="c".repeat(64),REQUEST="d".repeat(32);
    public static final long OWNER=9007199254740993L;
    public static final byte[] BYTES={1,2,3};
    public static final String SHA=GeneralTicketDocumentReplyService.sha256(BYTES);
    public static final List<String> TABLES=List.of("users","general_ticket_read_grants","general_ticket_operate_grants","tickets","messages","channels","chat_history","ticket_attributes","chat_attachment_metadata","general_ticket_document_replies");
    public static void schema(JdbcTemplate jdbc,boolean existingUsers){
        if(!existingUsers)jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,password TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE)");
        GeneralEntryIdentityFixture.schema(jdbc,true);
        jdbc.execute("""
            CREATE TABLE channels(id BIGINT PRIMARY KEY,platform TEXT);
            CREATE TABLE messages(group_msg_id BIGINT,user_id BIGINT,ticket_id TEXT,client_name TEXT,problem TEXT);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,status TEXT,created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                business_id BIGINT,is_general BOOLEAN,group_msg_id BIGINT,channel_id BIGINT,questionnaire_catalog_sha256 TEXT,general_selection_source TEXT,business_selection_source TEXT);
            CREATE TABLE chat_history(id BIGINT PRIMARY KEY,user_id BIGINT,ticket_owner_user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,sender TEXT,message TEXT,
                timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,message_type TEXT,attachment TEXT,deleted_at TIMESTAMPTZ,channel_id BIGINT,tg_message_id BIGINT);
            CREATE TABLE ticket_attributes(ticket_id TEXT,question_id TEXT,question_text TEXT,value_label TEXT,value_text TEXT);
            CREATE TABLE chat_attachment_metadata(id BIGINT PRIMARY KEY,chat_history_id BIGINT,ticket_id TEXT,channel_id BIGINT,storage_key TEXT,original_name TEXT,content_hash TEXT,size BIGINT,mime_type TEXT,
                deleted_at TIMESTAMPTZ,archived_at TIMESTAMPTZ,storage_class TEXT,normalization_status TEXT,availability_status TEXT,storage_provider TEXT);
            CREATE TABLE general_ticket_document_replies(ticket_user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,state TEXT,chat_history_id BIGINT,channel_id BIGINT,provider_message_id BIGINT,
                storage_key TEXT,original_name TEXT,content_sha256 TEXT,payload BYTEA,request_key TEXT);
            """);
    }
    public static void seed(JdbcTemplate jdbc,long id,String name,String password){
        jdbc.execute("TRUNCATE "+String.join(",",TABLES));
        jdbc.update("INSERT INTO users(id,username,password) VALUES(?,?,?),(55,'other','unused')",id,name,password);
        jdbc.update("INSERT INTO general_ticket_read_grants(user_id) VALUES(?),(55)",id);
        jdbc.update("INSERT INTO channels VALUES(9,'telegram'),(10,'vk')");
        jdbc.update("INSERT INTO messages VALUES(1,?,?,'private client','private problem'),(2,?,?,'vk client','vk problem')",OWNER,RAW,OWNER+1,RAW+"vk");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,status,is_general,group_msg_id,channel_id,questionnaire_catalog_sha256,general_selection_source) VALUES(?,?,?,'open',TRUE,1,9,?,'USER_SELECTION'),(?,?,?,'open',TRUE,2,10,?,'USER_SELECTION')",OWNER,RAW,PUBLIC,CATALOG,OWNER+1,RAW+"vk",VK_PUBLIC,CATALOG);
        jdbc.update("INSERT INTO ticket_attributes VALUES(?,'q1','private question','private label','private answer')",RAW);
        for(Surface surface:List.of(Surface.legacy,Surface.telegram,Surface.vk,Surface.document)){
            long h=historyId(surface),owner=surface==Surface.vk?OWNER+1:OWNER,channel=surface==Surface.vk?10:9;
            String raw=surface==Surface.vk?RAW+"vk":RAW,publicId=publicId(surface),key=storageKey(surface),type=surface==Surface.document?"general_operator_document":"document";
            jdbc.update("INSERT INTO chat_history(id,user_id,ticket_owner_user_id,ticket_id,ticket_public_id,sender,message,message_type,attachment,channel_id,tg_message_id) VALUES(?,?,?,?,?,?,'private history',?,?,?,33)",h,owner,owner,raw,publicId,surface==Surface.document?"operator":"client",type,key,channel);
            jdbc.update("INSERT INTO chat_attachment_metadata(id,chat_history_id,ticket_id,channel_id,storage_key,original_name,content_hash,size,mime_type,storage_class,normalization_status,availability_status,storage_provider) VALUES(?,?,?,?,?,'proof.bin',?,3,'application/octet-stream','dialog_attachment','normalized','available','s3')",h,h,raw,channel,key,surface==Surface.legacy?null:SHA);
            if(surface==Surface.document)jdbc.update("INSERT INTO general_ticket_document_replies VALUES(?,?,?,'SENT',?,9,33,?,'proof.bin',?,?,?)",owner,raw,publicId,h,key,SHA,BYTES,REQUEST);
        }
    }
    public static long historyId(Surface surface){return switch(surface){case legacy->1;case telegram->2;case vk->3;case document->4;default->throw new IllegalArgumentException();};}
    public static String publicId(Surface surface){return surface==Surface.vk?VK_PUBLIC:PUBLIC;}
    public static String storageKey(Surface surface){return switch(surface){case legacy->RAW+"/file.bin";case telegram->"questionnaires/telegram/9/general/"+OWNER+"/"+CATALOG+"/33/"+SHA+".bin";case vk->"questionnaires/vk/10/general/"+(OWNER+1)+"/"+CATALOG+"/33/1/"+SHA+".bin";case document->PUBLIC+"/general-"+REQUEST+".bin";default->throw new IllegalArgumentException();};}
    public static Authentication auth(long id,String name){return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"unused",true,List.of()),null,List.of());}
    public static Object call(GeneralTicketReadService service,Surface surface,Authentication actor){
        String name=switch(surface){case legacy,telegram,vk,document->"attachment";default->surface.name();};
        var methods=Arrays.stream(GeneralTicketReadService.class.getMethods()).filter(m->m.getName().equals(name)).toList();
        if(methods.size()!=1)throw new AssertionError("One public read entry required: "+name);
        var method=methods.get(0);Object bound=actor;
        if(method.getParameterTypes()[0]==long.class)bound=actor!=null&&actor.getPrincipal() instanceof PanelUserPrincipal p?p.getUserId():7L;
        Object[] args=switch(surface){case page->new Object[]{bound,10,0,null,null};case find,answers->new Object[]{bound,PUBLIC};case history->new Object[]{bound,PUBLIC,null,10};default->new Object[]{bound,publicId(surface),historyId(surface)};};
        try{return method.invoke(service,args);}catch(InvocationTargetException ex){if(ex.getCause() instanceof RuntimeException cause)throw cause;throw new AssertionError(ex.getCause());}catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
    }
    public static Map<String,List<String>> snapshot(JdbcTemplate jdbc){var result=new LinkedHashMap<String,List<String>>();for(String table:TABLES)result.put(table,jdbc.queryForList("SELECT to_jsonb(row)::text FROM "+table+" row ORDER BY to_jsonb(row)::text",String.class));return result;}
    public static void change(JdbcTemplate jdbc,String mode){switch(mode){
        case "rename"->jdbc.update("UPDATE users SET username='renamed' WHERE id=7");
        case "rename-reuse"->{jdbc.update("UPDATE users SET username='renamed' WHERE id=7");jdbc.update("UPDATE users SET username='caller' WHERE id=55");}
        case "delete-reuse"->{jdbc.update("DELETE FROM users WHERE id=7");jdbc.update("UPDATE users SET username='caller' WHERE id=55");}
        case "case"->jdbc.update("UPDATE users SET username='CALLER' WHERE id=7");
        case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");
        case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");
        case "duplicate"->jdbc.update("UPDATE users SET username='CALLER',enabled=FALSE WHERE id=55");
        case "read"->jdbc.update("UPDATE general_ticket_read_grants SET is_active=FALSE WHERE user_id=7");
        default->throw new IllegalArgumentException(mode);
    }}
    /** Controlled mutation at the actual owning statement boundary, not a concurrent-lock claim. */
    public static JdbcTemplate boundary(JdbcTemplate jdbc,IntConsumer before)throws Exception{
        Connection actual=jdbc.getDataSource().getConnection();var count=new AtomicInteger();
        var proxy=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,args)->{
            if(m.getName().equals("prepareStatement")&&args!=null&&args[0] instanceof String sql&&sql.contains("FROM tickets ticket"))before.accept(count.incrementAndGet());
            try{return m.invoke(actual,args);}catch(InvocationTargetException ex){throw ex.getCause();}
        });
        return new JdbcTemplate(new SingleConnectionDataSource(proxy,true));
    }
}
