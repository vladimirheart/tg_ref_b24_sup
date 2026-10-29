package com.example.panel.service;

import org.springframework.security.core.Authentication;

import com.example.panel.config.MultibusinessFeatureFlags;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Local GENERAL creation only; no bot/channel, business inference, legacy spans or delivery. */
@Service
public class GeneralManualTicketCreationService {
    private final JdbcTemplate plain;
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final UiEventOutboxAppendService ids;
    private final TransactionTemplate transaction;

    public GeneralManualTicketCreationService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,UiEventOutboxAppendService ids) {
        plain=jdbc;this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.ids=ids;
        transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public boolean enabled() {return flags.isFoundationEnabled()&&flags.isGeneralTicketQueue()&&flags.isManualTicketCreation();}
    public boolean canCreate(Authentication authentication) {
        return canCreate(GeneralTicketActor.from(authentication));
    }
    private boolean canCreate(GeneralTicketActor identity) {
        if(!enabled()||identity==null||!identity.valid())return false;
        try {return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users account WHERE account.id=:authenticatedActorId AND account.username=:identity AND "
            +GeneralTicketAccessService.OPERATE_ACCOUNT+" AND general_ticket_selection_source_allowed('MANUAL'))",
            identity.bind(new MapSqlParameterSource()),Boolean.class));
        } catch(DataAccessException ex) {return false;}
    }
    public static boolean validInput(String key,String name,String problem) {
        return key!=null&&key.matches("[a-f0-9]{32}")&&validText(name,200,false)&&validText(problem,10000,true);
    }
    private static boolean validText(String input,int limit,boolean lines) {
        if(input==null||input.isBlank()||input.strip().length()>limit)return false;
        if(input.codePoints().anyMatch(c->Character.isISOControl(c)&&!(lines&&(c=='\n'||c=='\r'||c=='\t'))))return false;
        for(int i=0;i<input.length();i++) {
            char c=input.charAt(i);
            if(Character.isHighSurrogate(c)) {if(++i>=input.length()||!Character.isLowSurrogate(input.charAt(i)))return false;}
            else if(Character.isLowSurrogate(c))return false;
        }
        return true;
    }
    public Optional<String> create(Authentication authentication,String key,String clientName,String problem) {
        return create(GeneralTicketActor.from(authentication),key,clientName,problem);
    }
    private Optional<String> create(GeneralTicketActor identity,String key,String clientName,String problem) {
        if(!enabled()||identity==null||!identity.valid()||!validInput(key,clientName,problem))return Optional.empty();
        var args=identity.bind(new MapSqlParameterSource()).addValue("key",key)
            .addValue("name",clientName.strip()).addValue("problem",problem.strip());
        try {return transaction.execute(tx->{
            var actors=jdbc.query("""
                SELECT account.id,account.username FROM users account
                JOIN general_ticket_read_grants read_access ON read_access.user_id=account.id AND read_access.is_active=TRUE
                JOIN general_ticket_operate_grants operation ON operation.user_id=account.id AND operation.is_active=TRUE
                WHERE account.id=:authenticatedActorId AND account.username=:identity AND
                """+GeneralTicketAccessService.CURRENT_ACCOUNT+"""
                AND general_ticket_selection_source_allowed('MANUAL')
                FOR SHARE OF account,read_access,operation
                """,args,(rs,row)->new Actor(rs.getLong("id"),rs.getString("username")));
            if(actors.size()!=1||!enabled())return Optional.<String>empty();
            var actor=actors.get(0);
            // Same-key requests serialize before lookup/allocation; different actors cannot claim a saved key.
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended('general-manual:'||:key,0))",args);
            String fingerprint=jdbc.queryForObject("SELECT encode(sha256(convert_to(jsonb_build_array(CAST(:name AS text),CAST(:problem AS text))::text,'UTF8')),'hex')",args,String.class);
            var prior=jdbc.query("""
                SELECT request.actor_user_id,request.input_sha256,request.ticket_public_id,
                    EXISTS(SELECT 1 FROM tickets ticket
                        JOIN messages root ON root.group_msg_id=ticket.group_msg_id AND root.user_id=ticket.user_id AND root.ticket_id=ticket.ticket_id
                        JOIN manual_general_clients local_client ON local_client.user_id=ticket.user_id
                        WHERE ticket.user_id=request.ticket_user_id AND ticket.ticket_id=request.ticket_id AND ticket.ticket_public_id=request.ticket_public_id
                            AND ticket.is_general=TRUE AND ticket.business_id IS NULL AND ticket.general_selection_source='MANUAL'
                            AND 1=(SELECT COUNT(*) FROM tickets collision WHERE collision.ticket_id=ticket.ticket_id)) AS current_owner
                FROM general_manual_ticket_requests request WHERE request.request_key=:key
                """,args,(rs,row)->new Previous(rs.getLong("actor_user_id"),rs.getString("input_sha256"),rs.getString("ticket_public_id"),rs.getBoolean("current_owner")));
            if(!prior.isEmpty()) {
                var saved=prior.get(0);
                return saved.actorId()==actor.id()&&saved.fingerprint().equals(fingerprint)&&saved.currentOwner()&&enabled()
                    ?Optional.of(saved.publicId()):Optional.<String>empty();
            }
            if(!enabled())return Optional.<String>empty();
            long clientId=allocateClientId();String publicId=UUID.randomUUID().toString().replace("-","");
            args.addValue("clientId",clientId).addValue("publicId",publicId).addValue("ticketId","general-manual-"+publicId)
                .addValue("actorId",actor.id()).addValue("actor",actor.name()).addValue("fingerprint",fingerprint);
            int inserted=jdbc.update("INSERT INTO manual_general_clients(user_id,display_name,created_by_user_id) "
                +"SELECT :clientId,:name,account.id FROM users account WHERE account.id=:actorId AND account.id=:authenticatedActorId AND account.username=:identity AND "
                +GeneralTicketAccessService.OPERATE_ACCOUNT,args);
            if(inserted!=1||!enabled())throw new IllegalStateException("GENERAL creation access changed");
            jdbc.update("""
                INSERT INTO messages(group_msg_id,user_id,ticket_id,client_name,problem,created_at,created_date,created_time)
                VALUES(:clientId,:clientId,:ticketId,:name,:problem,CURRENT_TIMESTAMP,
                    to_char(CURRENT_TIMESTAMP,'YYYY-MM-DD'),to_char(CURRENT_TIMESTAMP,'HH24:MI:SS'))
                """,args);
            jdbc.update("""
                INSERT INTO tickets(user_id,ticket_id,group_msg_id,ticket_public_id,business_id,is_general,general_selection_source,
                    status,created_at,reopen_count,closed_count,work_time_total_sec)
                VALUES(:clientId,:ticketId,:clientId,:publicId,NULL,TRUE,'MANUAL','pending',CURRENT_TIMESTAMP,0,0,0)
                """,args);
            jdbc.update("""
                INSERT INTO chat_history(user_id,sender,message,timestamp,ticket_id,message_type,ticket_owner_user_id,ticket_public_id)
                VALUES(:clientId,'operator',:problem,CURRENT_TIMESTAMP,:ticketId,'operator_note',:clientId,:publicId)
                """,args);
            jdbc.update("""
                INSERT INTO general_manual_ticket_requests(request_key,actor_user_id,actor,input_sha256,ticket_user_id,ticket_id,ticket_public_id)
                VALUES(:key,:actorId,:actor,:fingerprint,:clientId,:ticketId,:publicId)
                """,args);
            jdbc.update("""
                INSERT INTO ui_event_outbox(id,event_type,ticket_id,created_at,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id)
                VALUES(:eventId,'ticket_created',:ticketId,CURRENT_TIMESTAMP,:clientId,:publicId,'GENERAL',NULL)
                """,args.addValue("eventId",ids.nextEventId()));
            return Optional.of(publicId);
        });} catch(RuntimeException ex) {return Optional.empty();}
    }
    private long allocateClientId() {
        for(int attempt=0;attempt<32;attempt++) {
            Long id=plain.queryForObject("SELECT nextval('manual_client_user_id_seq')",Long.class);
            if(id==null||id>=0)throw new IllegalStateException("Local client namespace exhausted");
            Boolean occupied=plain.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM tickets WHERE user_id=?) OR EXISTS(SELECT 1 FROM messages WHERE user_id=? OR group_msg_id=?)
                    OR EXISTS(SELECT 1 FROM bot_users WHERE user_id=?) OR EXISTS(SELECT 1 FROM manual_business_clients WHERE user_id=?)
                    OR EXISTS(SELECT 1 FROM manual_general_clients WHERE user_id=?)
                """,Boolean.class,id,id,id,id,id,id);
            if(Boolean.FALSE.equals(occupied))return id;
        }
        throw new IllegalStateException("Local client namespace unavailable");
    }
    private record Actor(long id,String name) { }
    private record Previous(long actorId,String fingerprint,String publicId,boolean currentOwner) { }
}
