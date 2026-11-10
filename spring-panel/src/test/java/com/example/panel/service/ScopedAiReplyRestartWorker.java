package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import java.net.URI;
import java.nio.file.*;
import java.util.function.BooleanSupplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Disposable JVM crash probe. Refuses runtime endpoints/config; its provider is a local file marker. */
public final class ScopedAiReplyRestartWorker {
    private ScopedAiReplyRestartWorker() { }
    static void validate(String[] args) {
        if(args.length!=5 || !args[1].matches("[a-f0-9]{32}") || !args[2].matches("[1-9][0-9]*")
            || !(args[3].equals("QUEUED") || args[3].equals("SENDING")) || !args[0].startsWith("jdbc:postgresql://"))
            throw new IllegalArgumentException("Owned fixture inputs required");
        var endpoint=URI.create(args[0].substring(5));
        if(!java.util.Set.of("127.0.0.1","localhost").contains(endpoint.getHost()) || endpoint.getPort()<=1024
            || endpoint.getPort()==5432 || endpoint.getPort()>65535 || endpoint.getUserInfo()!=null
            || !java.util.Set.of("/r290_edit","/iguana_test").contains(endpoint.getPath()) || endpoint.getFragment()!=null)
            throw new IllegalArgumentException("Owned loopback database required");
        var query=endpoint.getRawQuery();
        if(query==null || !query.matches("(?:loggerLevel=OFF&)?currentSchema=r313_auto_reply_[a-f0-9]{12}"))
            throw new IllegalArgumentException("Isolated auto-reply schema required");
        var marker=Path.of(args[4]).toAbsolutePath().normalize();
        if(!marker.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())
            || !marker.getFileName().toString().equals("provider.txt")
            || !marker.getParent().getFileName().toString().startsWith("r314-ai-restart-"))
            throw new IllegalArgumentException("Owned temporary marker required");
    }
    public static void main(String[] args)throws Exception {
        validate(args);
        boolean nativeFixture=URI.create(args[0].substring(5)).getPath().equals("/r290_edit");
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(args[0],nativeFixture?"r290_test":"iguana",nativeFixture?"":"iguana"));
        if(!args[1].equals(jdbc.queryForObject("SELECT nonce FROM r314_restart_owner",String.class)))
            throw new IllegalStateException("Fixture ownership mismatch");
        long event=Long.parseLong(args[2]);
        var scope=jdbc.queryForObject("SELECT * FROM ui_event_outbox WHERE id=?",(rs,n)->new UiEventTicketScope(
            rs.getLong("ticket_user_id"),rs.getString("ticket_id"),rs.getString("ticket_public_id"),rs.getString("ticket_scope_kind"),rs.getLong("ticket_business_id")),event);
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
        var config=new DialogAiAssistantConfigService(jdbc,null,null,null) {
            @Override public boolean isAgentEnabled(){return true;}
            @Override public String resolveAgentMode(){return "auto_reply";}
            @Override public ScopedAutoReplyLimits scopedAutoReplyLimits(){return new ScopedAutoReplyLimits(3,60,0);}
        };
        var provider=new DialogReplyTransportService(null,null,null) {
            @Override public DialogReplyTransportResult sendTextGuarded(Channel channel,Long user,String message,Long replyTo,BooleanSupplier guard) {
                if(!guard.getAsBoolean()) throw new IllegalStateException("Fixture guard denied");
                try {Files.writeString(Path.of(args[4]),"provider-boundary",StandardOpenOption.CREATE_NEW);}
                catch(java.io.IOException ex){throw new IllegalStateException(ex);}
                Runtime.getRuntime().halt(0); // Abrupt exit after durable SENDING; no receipt/result transaction commit.
                throw new AssertionError("halt returned");
            }
        };
        var service=new ScopedAiAutoReplyService(jdbc,flags,config,provider,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)));
        if(!service.prepare(scope,event)) throw new IllegalStateException("Fixture queue was not committed");
        if(args[3].equals("SENDING")) service.dispatch(scope,event);
        else Runtime.getRuntime().halt(0); // Abrupt exit with a committed, never-attempted QUEUED row.
        throw new IllegalStateException("Fixture did not reach crash point");
    }
}
