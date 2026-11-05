package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.repository.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Real owning SQL and transactions. Native override accepts only this test's nonce-owned disposable database. */
class BotRuntimeOwnerClientEditPostgresTest {
    static final long USER=9_007_199_254_740_993L, CHANNEL=USER+2, MESSAGE=Long.MAX_VALUE;
    static final String RAW=" padded/Ю%?' ";
    Fixture fixture;
    static Stream<Arguments> owners(){return Stream.of("telegram","vk","max").flatMap(p->Stream.of("business","general","unresolved").map(s->Arguments.of(p,s)));}
    static JdbcTemplate freshJdbc(){
        String url=System.getProperty("r290.edit.jdbc");
        if(url==null)return PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("r290_owner_edit");
        String nonce=System.getProperty("r290.edit.nonce","");
        if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r290_edit")||!nonce.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Owned native test endpoint required");
        int port=URI.create(url.substring(5)).getPort();if(port<=1024||port==5432||port>65535)throw new IllegalArgumentException("Disposable port required");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,"r290_test",""));
        if(!nonce.equals(admin.queryForObject("SELECT nonce FROM r290_native_owner",String.class)))throw new IllegalStateException("Wrong test cluster");
        String schema="r290_edit_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE SCHEMA "+schema);
        return new JdbcTemplate(new DriverManagerDataSource(url+"?currentSchema="+schema,"r290_test",""));
    }
    static final class Boundary extends JdbcTemplate {
        Runnable before;
        Boundary(JdbcTemplate base){super(base.getDataSource());}
        @Override public int update(String sql,Object... args){if(sql.contains("UPDATE chat_history AS history")&&before!=null){var action=before;before=null;action.run();}return super.update(sql,args);}
    }
    static final class Fixture {
        final JdbcTemplate base=freshJdbc();final Boundary jdbc=new Boundary(base);
        final UiEventOutboxAppendService ui=mock(UiEventOutboxAppendService.class);
        final BotRuntimeTicketWriteService service=new BotRuntimeTicketWriteService(jdbc,mock(DialogReplyTargetService.class),mock(DialogResponsibilityService.class),mock(DialogParticipantService.class),ui,mock(ProviderDeliveryLedgerService.class),mock(PendingFeedbackRequestRepository.class),mock(FeedbackRepository.class),new LegacyTicketIdJdbcGuard(jdbc));
        final TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        Fixture(String platform,String scope){
            base.execute("CREATE TABLE channels(id bigint PRIMARY KEY,platform text,is_active boolean)");
            base.execute("CREATE TABLE tickets(user_id bigint,ticket_id text,status text,channel_id bigint,ticket_public_id text,business_id bigint,is_general boolean,PRIMARY KEY(user_id,ticket_id))");
            base.execute("CREATE TABLE chat_history(id bigint PRIMARY KEY,ticket_id text,user_id bigint,channel_id bigint,tg_message_id bigint,sender text,message text,original_message text,edited_at timestamp)");
            base.update("INSERT INTO channels VALUES(?,?,true)",CHANNEL,platform);root(USER,RAW,scope);history(1,USER,RAW);
        }
        void root(long user,String raw,String scope){base.update("INSERT INTO tickets VALUES(?,?,'pending',?,'public-r290',?,?)",user,raw,CHANNEL,scope.equals("business")?7L:null,scope.equals("unresolved")?null:scope.equals("general"));}
        void history(long id,long user,String raw){base.update("INSERT INTO chat_history VALUES(?,?,?,?,?,'client','old text',NULL,NULL)",id,raw,user,CHANNEL,MESSAGE);}
        String snapshot(){return base.queryForObject("SELECT jsonb_build_object('tickets',(SELECT jsonb_agg(to_jsonb(t) ORDER BY user_id,ticket_id) FROM tickets t),'history',(SELECT jsonb_agg(to_jsonb(h) ORDER BY id) FROM chat_history h),'channels',(SELECT jsonb_agg(to_jsonb(c) ORDER BY id) FROM channels c))::text",String.class);}
        BotRuntimeTicketWriteService.MutationResult edit(String platform,Long channel,Long user,Long message,String text){
            return tx.execute(status->{try{
                return (BotRuntimeTicketWriteService.MutationResult)service.getClass().getMethod("markClientMessageEdited",String.class,Long.class,Long.class,Long.class,String.class).invoke(service,platform,channel,user,message,text);
            }catch(NoSuchMethodException baseline){return service.markClientMessageEdited(channel,message,text);}
            catch(InvocationTargetException failure){throw (RuntimeException)failure.getCause();}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}});
        }
    }
    @AfterEach void cleanup(){if(fixture!=null){String schema=fixture.base.queryForObject("SELECT current_schema()",String.class);assertThat(schema).matches("r290_(?:edit_[a-f0-9]{32}|owner_edit_[a-f0-9]{12})");fixture.base.execute("DROP SCHEMA "+schema+" CASCADE");}}
    @ParameterizedTest @MethodSource("owners")
    void matchingSingleMessagePreservesRootAndOriginalText(String platform,String scope){
        var f=fixture=new Fixture(platform,scope);assertThat(f.edit(platform,CHANNEL,USER,MESSAGE," edited ").updated()).isTrue();
        assertThat(f.base.queryForMap("SELECT message,original_message,edited_at FROM chat_history")).containsEntry("message","edited").containsEntry("original_message","old text").doesNotContainValue(null);
        verify(f.ui).publishClientMessageEdited(argThat(c->RAW.equals(c.ticketId())&&c.userId()==USER&&c.channelId()==CHANNEL),eq(" edited "));
        f.edit(platform,CHANNEL,USER,MESSAGE," second ");assertThat(f.base.queryForObject("SELECT original_message FROM chat_history",String.class)).isEqualTo("old text");
    }
    @ParameterizedTest @MethodSource("owners")
    void anotherUsersOnlyMatchingProviderIdCannotBeEdited(String platform,String scope){
        var f=fixture=new Fixture(platform,scope);f.base.update("UPDATE tickets SET user_id=?",USER+1);f.base.update("UPDATE chat_history SET user_id=?",USER+1);String before=f.snapshot();
        assertThat(f.edit(platform,CHANNEL,USER,MESSAGE," edited ").updated()).isFalse();assertThat(f.snapshot()).isEqualTo(before);verifyNoInteractions(f.ui);
    }
    @ParameterizedTest @MethodSource("owners")
    void sameProviderIdInTwoPrivateChatsUpdatesOnlyItsAuthor(String platform,String scope){
        var f=fixture=new Fixture(platform,scope);f.root(USER+1,"foreign",scope);f.history(2,USER+1,"foreign");
        assertThat(f.edit(platform,CHANNEL,USER,MESSAGE," edited ").updated()).isTrue();assertThat(f.base.queryForObject("SELECT message FROM chat_history WHERE id=2",String.class)).isEqualTo("old text");
        verify(f.ui).publishClientMessageEdited(argThat(c->RAW.equals(c.ticketId())&&c.userId()==USER),eq(" edited "));
    }
    static Stream<Arguments> drifts(){return Stream.of("telegram","vk","max").flatMap(p->Stream.of("root-owner","root-channel","root-public","root-business","root-general","root-missing","root-duplicate","history-owner","history-sender","history-provider","history-duplicate","platform","inactive","channel-missing").map(d->Arguments.of(p,d)));}
    @ParameterizedTest @MethodSource("drifts")
    void owningWriteRejectsChangedRootHistoryOrChannel(String platform,String drift){
        var f=fixture=new Fixture(platform,"general");String[] afterDrift={null};
        f.jdbc.before=()->{switch(drift){
            case "root-owner"->f.base.update("UPDATE tickets SET user_id=?",USER+1);
            case "root-channel"->f.base.update("UPDATE tickets SET channel_id=1");
            case "root-public"->f.base.update("UPDATE tickets SET ticket_public_id='recreated'");
            case "root-business"->f.base.update("UPDATE tickets SET business_id=8");
            case "root-general"->f.base.update("UPDATE tickets SET is_general=false");
            case "root-missing"->f.base.update("DELETE FROM tickets");
            case "root-duplicate"->f.root(USER+1,RAW,"business");
            case "history-owner"->f.base.update("UPDATE chat_history SET user_id=?",USER+1);
            case "history-sender"->f.base.update("UPDATE chat_history SET sender='operator'");
            case "history-provider"->f.base.update("UPDATE chat_history SET tg_message_id=1");
            case "history-duplicate"->f.history(2,USER,RAW);
            case "platform"->f.base.update("UPDATE channels SET platform=?",platform.equals("max")?"vk":"max");
            case "inactive"->f.base.update("UPDATE channels SET is_active=false");
            case "channel-missing"->f.base.update("DELETE FROM channels");
        }afterDrift[0]=f.snapshot();};
        assertThat(f.edit(platform,CHANNEL,USER,MESSAGE," edited ").updated()).isFalse();assertThat(afterDrift[0]).isNotNull();assertThat(f.snapshot()).isEqualTo(afterDrift[0]);verifyNoInteractions(f.ui);
    }
    static Stream<Arguments> invalid(){return Stream.of(Arguments.of(null,CHANNEL,USER,MESSAGE,"edited"),Arguments.of("MAX",CHANNEL,USER,MESSAGE,"edited"),Arguments.of("unknown",CHANNEL,USER,MESSAGE,"edited"),Arguments.of("max ",CHANNEL,USER,MESSAGE,"edited"),Arguments.of("max",0L,USER,MESSAGE,"edited"),Arguments.of("max",CHANNEL,null,MESSAGE,"edited"),Arguments.of("max",CHANNEL,0L,MESSAGE,"edited"),Arguments.of("max",CHANNEL,-1L,MESSAGE,"edited"),Arguments.of("max",CHANNEL,USER,0L,"edited"),Arguments.of("max",CHANNEL,USER,-1L,"edited"),Arguments.of("max",CHANNEL,USER,MESSAGE," "));}
    @ParameterizedTest @MethodSource("invalid")
    void invalidIdentityNeverWrites(String platform,Long channel,Long user,Long message,String text){var f=fixture=new Fixture("max","business");String before=f.snapshot();assertThat(f.edit(platform,channel,user,message,text).updated()).isFalse();assertThat(f.snapshot()).isEqualTo(before);verifyNoInteractions(f.ui);}
    @Test void uiFailureRollsBackTheOwningHistoryUpdate(){var f=fixture=new Fixture("max","business");String before=f.snapshot();doThrow(new IllegalStateException("r290 UI failure")).when(f.ui).publishClientMessageEdited(any(UiEventOutboxAppendService.TicketContext.class),anyString());assertThatThrownBy(()->f.edit("max",CHANNEL,USER,MESSAGE,"edited")).hasMessageContaining("r290 UI failure");assertThat(f.snapshot()).isEqualTo(before);}
}
