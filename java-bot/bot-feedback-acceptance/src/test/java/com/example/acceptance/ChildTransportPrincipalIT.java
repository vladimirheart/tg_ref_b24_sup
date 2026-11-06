package com.example.acceptance;

import static org.assertj.core.api.Assertions.*;
import com.example.supportbot.config.ChildTransportDatabase;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;

/** Opt-in actual bot startup SQL against the backend's V86 and separately provisioned login roles. */
class ChildTransportPrincipalIT {
    static PostgreSQLContainer<?> postgres;
    static String url;
    static JdbcTemplate admin;
    @BeforeAll static void database() throws Exception {
        url=System.getProperty("r291.principals.jdbc","");String nonce=System.getProperty("r291.principals.nonce","");
        String adminPassword="";
        if(url.isEmpty()) {
            nonce=System.getProperty("max.media.acceptance.nonce",UUID.randomUUID().toString().replace("-",""));
            if(!nonce.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Owned run nonce required");
            adminPassword="r291-test-only";
            postgres=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("r291_principals")
                .withUsername("r290_test").withPassword(adminPassword)
                .withLabel("io.iguana.owner",System.getProperty("max.media.acceptance.nonce")!=null?"01-278-r287-max-media":"01-278-r291-principals")
                .withLabel("io.iguana.test-nonce",nonce)
                .withCreateContainerCmdModifier(c->c.getHostConfig().withMemory(192L*1024*1024).withNanoCPUs(1_000_000_000L)
                    .withPortBindings(new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",0),new com.github.dockerjava.api.model.ExposedPort(5432))));
            postgres.start();url="jdbc:postgresql://127.0.0.1:"+postgres.getMappedPort(5432)+"/r291_principals";
            var owner=new JdbcTemplate(new DriverManagerDataSource(url,"r290_test",adminPassword));
            owner.execute("CREATE TABLE r290_native_owner(nonce text NOT NULL)");owner.update("INSERT INTO r290_native_owner VALUES(?)",nonce);
        }
        if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r291_principals")||!nonce.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Owned test endpoint required");
        int port=java.net.URI.create(url.substring(5)).getPort();if(port<=1024||port==5432||port>65535)throw new IllegalArgumentException("Owned random port required");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,"r290_test",adminPassword));
        if(!nonce.equals(admin.queryForObject("SELECT nonce FROM r290_native_owner",String.class)))throw new IllegalStateException("Wrong native cluster");
        if(postgres!=null) {
            admin.execute("CREATE TABLE channels(id bigint PRIMARY KEY,platform text,is_active boolean);CREATE TABLE tickets(user_id bigint,ticket_id text,channel_id bigint);CREATE TABLE settings(secret text)");
            admin.execute("INSERT INTO channels VALUES(22,'max',true),(23,'max',true),(24,'telegram',true),(25,'vk',true)");
            for(String file:List.of("V24__integration_transport_outbox.sql","V85__outbound_delivery_receipts.sql","V86__child_transport_principals.sql"))
                admin.execute(new ClassPathResource("db/"+file).getContentAsString(StandardCharsets.UTF_8));
            String provision=Files.readString(Path.of("../../scripts/sql/provision-child-transport.sql")).replaceAll("(?m)^\\\\[^\\r\\n]*[\\r\\n]+","");
            for(int id:List.of(22,23,24,25))admin.execute(provision.replace(":'channel_id'","'"+id+"'")
                .replace(":'platform'","'"+(id==24?"telegram":id==25?"vk":"max")+"'").replace(":'password'","'"+"a".repeat(64)+"'"));
        }
    }
    @AfterAll static void stop(){if(postgres!=null)postgres.stop();}
    @BeforeEach void currentChannel(){admin.execute("UPDATE channels SET is_active=true,platform=CASE id WHEN 24 THEN 'telegram' WHEN 25 THEN 'vk' ELSE 'max' END;UPDATE bot_child_principals SET enabled=true");}
    private MockEnvironment environment(int id){return new MockEnvironment().withProperty("app.integration.transport.mode","rabbitmq")
        .withProperty("app.bot-runtime.channel-id",Integer.toString(id)).withProperty("app.bot-runtime.platform",id==24?"telegram":id==25?"vk":"max")
        .withProperty("app.integration.panel-api.token","iguana-child-v1:"+(id==24?"telegram":id==25?"vk":"max")+":"+id+":"+"a".repeat(64));}
    private void validate(int login,int channel){ChildTransportDatabase.validate(new DriverManagerDataSource(url,"iguana_child_"+login,"a".repeat(64)),environment(channel));}
    @ParameterizedTest @ValueSource(ints={22,24,25})
    void realChannelRolePassesActualBotStartupSql(int id){assertThatCode(()->validate(id,id)).doesNotThrowAnyException();}
    @Test void foreignSqlLoginCannotBorrowTheApiPrincipal(){assertThatThrownBy(()->validate(23,22)).isInstanceOf(IllegalStateException.class);}
    @ParameterizedTest @ValueSource(strings={"business-table","public-table","sequence","role-membership","security-definer","truncate","receipt-context","bypass-rls","inactive","revoked","platform-rebind"})
    void realPrivilegeAndRevocationFailuresAreRejectedBeforeStartup(String change) {
        String undo;
        switch(change) {
            case "business-table"->{admin.execute("GRANT SELECT ON settings TO iguana_child_22");undo="REVOKE SELECT ON settings FROM iguana_child_22";}
            case "public-table"->{admin.execute("GRANT SELECT ON settings TO PUBLIC");undo="REVOKE SELECT ON settings FROM PUBLIC";}
            case "sequence"->{admin.execute("CREATE SEQUENCE r291_leaky;GRANT USAGE ON SEQUENCE r291_leaky TO iguana_child_22");undo="DROP SEQUENCE r291_leaky";}
            case "role-membership"->{admin.execute("CREATE ROLE r291_member NOLOGIN;GRANT r291_member TO iguana_child_22");undo="REVOKE r291_member FROM iguana_child_22;DROP ROLE r291_member";}
            case "security-definer"->{admin.execute("CREATE FUNCTION r291_leak() RETURNS integer LANGUAGE sql SECURITY DEFINER AS 'SELECT 1'");undo="DROP FUNCTION r291_leak()";}
            case "truncate"->{admin.execute("GRANT TRUNCATE ON integration_transport_outbox TO iguana_child_22");undo="REVOKE TRUNCATE ON integration_transport_outbox FROM iguana_child_22";}
            case "receipt-context"->{admin.execute("GRANT UPDATE(attempt_id) ON integration_outbound_delivery_receipts TO iguana_child_22");undo="REVOKE UPDATE(attempt_id) ON integration_outbound_delivery_receipts FROM iguana_child_22";}
            case "bypass-rls"->{admin.execute("ALTER ROLE iguana_child_22 BYPASSRLS");undo="ALTER ROLE iguana_child_22 NOBYPASSRLS";}
            case "inactive"->{admin.execute("UPDATE channels SET is_active=false WHERE id=22");undo="UPDATE channels SET is_active=true WHERE id=22";}
            case "revoked"->{admin.execute("UPDATE bot_child_principals SET enabled=false WHERE channel_id=22");undo="UPDATE bot_child_principals SET enabled=true WHERE channel_id=22";}
            default->{admin.execute("UPDATE channels SET platform='vk' WHERE id=22");undo="UPDATE channels SET platform='max' WHERE id=22";}
        }
        try {assertThatThrownBy(()->validate(22,22)).isInstanceOf(IllegalStateException.class);}
        finally {admin.execute(undo);}
        assertThatCode(()->validate(22,22)).doesNotThrowAnyException();
    }
}
