package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.entity.Channel;
import com.example.panel.repository.ChildInfrastructureRepository;
import com.example.panel.security.ChildBotCredentials;
import com.example.panel.security.ChildBotCredentials.Principal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

/** Own nonce-checked disposable PostgreSQL; never loads application credentials. */
class ChildInfrastructurePostgresTest {
    static final String MASTER="infra288-disposable-only-012345678901234567890";
    static String url,password="";
    static JdbcTemplate jdbc;
    static org.testcontainers.containers.PostgreSQLContainer<?> container;
    static AtomicLong ids=new AtomicLong(28800);
    ChildInfrastructureRepository repository;
    @BeforeAll static void setup() throws Exception {
        url=System.getProperty("infra288.jdbc","");
        String nonce=System.getProperty("infra288.nonce","");
        if(url.isEmpty()) {
            password="infra288-test-only";nonce=UUID.randomUUID().toString().replace("-","");
            container=new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("infra288").withUsername("infra_test").withPassword(password)
                .withLabel("io.iguana.owner","01-288-infrastructure").withLabel("io.iguana.test-nonce",nonce);
            container.start();url=container.getJdbcUrl();
            var owner=new JdbcTemplate(new DriverManagerDataSource(url,"infra_test",password));
            owner.execute("CREATE TABLE infra288_owner(nonce text)");owner.update("INSERT INTO infra288_owner VALUES(?)",nonce);
        }
        var uri=java.net.URI.create(url.substring(5));
        if(!"127.0.0.1".equals(uri.getHost()) && !"localhost".equals(uri.getHost()))throw new IllegalArgumentException("Loopback required");
        if(!"/infra288".equals(uri.getPath()) || uri.getPort()<=1024 || uri.getPort()==5432 || !nonce.matches("[a-f0-9]{32}"))
            throw new IllegalArgumentException("Disposable cluster required");
        jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"infra_test",password));
        assertThat(jdbc.queryForObject("SELECT nonce FROM infra288_owner",String.class)).isEqualTo(nonce);
        jdbc.execute("CREATE TABLE channels(id bigint PRIMARY KEY, platform text, is_active boolean)");
        jdbc.execute("CREATE TABLE settings(secret text)");
        for(String migration:List.of("V24__integration_transport_outbox.sql","V85__outbound_delivery_receipts.sql",
                "V86__child_transport_principals.sql","V94__automatic_child_infrastructure.sql"))
            jdbc.execute(sql(migration));
    }
    @AfterAll static void cleanup(){if(container!=null)container.stop();}
    @BeforeEach void repository(){repository=new ChildInfrastructureRepository(jdbc);}
    static String sql(String migration) throws Exception {
        return new ClassPathResource("db/migration/postgresql/"+migration).getContentAsString(StandardCharsets.UTF_8);
    }
    Channel channel(String platform) {
        var c=new Channel();c.setId(ids.incrementAndGet());c.setPlatform(platform);c.setActive(true);
        jdbc.update("INSERT INTO channels VALUES(?,?,true)",c.getId(),platform);return c;
    }
    ChildInfrastructureService service(ChildRabbitProvisioner broker) {
        return new ChildInfrastructureService(repository,broker,new MockEnvironment()
            .withProperty("app.integration.transport.mode","rabbitmq").withProperty("app.bots.internal-api.token",MASTER));
    }
    @ParameterizedTest @ValueSource(strings={"telegram","max","vk"})
    void preparesSqlAndBrokerWithLeastPrivilegeAndNoControlPlaneAccess(String platform) throws Exception {
        var c=channel(platform);
        try(var api=new ChildRabbitProvisionerTest.Broker()) {
            assertThat(service(api.client()).prepare(c).state()).isEqualTo("ready");
            String role="iguana_child_"+c.getId();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bot_child_infrastructure WHERE channel_id=? AND ready_at IS NOT NULL",Integer.class,c.getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls FROM pg_roles WHERE rolname=?",Boolean.class,role)).isFalse();
            var child=new JdbcTemplate(new DriverManagerDataSource(url,role,ChildBotCredentials.databasePassword(MASTER,null,new Principal(c.getId(),platform))));
            assertThat(child.queryForObject("SELECT bot_child_channel()",Long.class)).isEqualTo(c.getId());
            denied(()->child.queryForList("SELECT * FROM settings"));
            denied(()->child.queryForList("SELECT * FROM bot_child_infrastructure"));
            denied(()->child.queryForObject("SELECT managed FROM bot_provision_child_transport(?,?,?,?)",Boolean.class,c.getId(),platform,"a".repeat(64),ChildRabbitProvisionerTest.HASH));
            denied(()->child.execute("CREATE ROLE infra288_escape"));
        }
    }
    private static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).rootCause().isInstanceOfSatisfying(java.sql.SQLException.class,
            ex -> assertThat(ex.getSQLState()).isEqualTo("42501"));
    }
    @Test void committedSqlAndLostBrokerResponseAreRecoveredWithoutChangingIdentity() throws Exception {
        var c=channel("max");
        try(var api=new ChildRabbitProvisionerTest.Broker()) {
            api.failAfterUserCreate=true;var service=service(api.client());
            assertThat(service.prepare(c).state()).isEqualTo("failed");
            var before=jdbc.queryForMap("SELECT role_oid,credential_digest,rabbit_password_hash FROM bot_child_infrastructure WHERE channel_id=?",c.getId());
            assertThat(service.prepare(c).state()).isEqualTo("ready");
            assertThat(jdbc.queryForMap("SELECT role_oid,credential_digest,rabbit_password_hash FROM bot_child_infrastructure WHERE channel_id=?",c.getId())).isEqualTo(before);
            assertThat(api.userPuts).isEqualTo(1);
        }
    }
    @Test void concurrentRequestsShareOneSqlIdentityAndSerializeBrokerChanges() throws Exception {
        var c=channel("telegram");
        try(var api=new ChildRabbitProvisionerTest.Broker()) {
            var service=service(api.client());var executor=Executors.newFixedThreadPool(2);
            try {
                var first=executor.submit(()->service.prepare(c));var second=executor.submit(()->service.prepare(c));
                assertThat(first.get().ready()).isTrue();assertThat(second.get().ready()).isTrue();
                assertThat(api.userPuts).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM bot_child_infrastructure WHERE channel_id=?",Integer.class,c.getId())).isEqualTo(1);
            } finally {executor.shutdownNow();}
        }
    }
    @Test void refusesUnownedRoleBeforeAnyBrokerOperation() {
        var c=channel("max");jdbc.execute("CREATE ROLE iguana_child_"+c.getId());
        var broker=mock(ChildRabbitProvisioner.class);
        assertThat(service(broker).prepare(c).state()).isEqualTo("failed");verifyNoInteractions(broker);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bot_child_principals WHERE channel_id=?",Integer.class,c.getId())).isZero();
    }
    @Test void revokeBetweenSqlAndBrokerPhasePreventsBrokerMutation() {
        var c=channel("max");var principal=new Principal(c.getId(),"max");
        repository.prepare(principal,"a".repeat(64),ChildRabbitProvisionerTest.HASH);
        jdbc.update("UPDATE bot_child_principals SET enabled=false WHERE channel_id=?",c.getId());
        var called=new java.util.concurrent.atomic.AtomicBoolean();
        assertThatThrownBy(()->repository.complete(principal,hash->called.set(true))).isInstanceOf(IllegalStateException.class);
        assertThat(called.get()).isFalse();
    }
    @Test void preservesManualProvisioningAndDoesNotAdoptItsBrokerAccount() {
        var c=channel("max");jdbc.execute("CREATE ROLE iguana_child_"+c.getId()+" LOGIN");
        jdbc.update("INSERT INTO bot_child_principals VALUES(?,?,?,true)","iguana_child_"+c.getId(),c.getId(),"max");
        var broker=mock(ChildRabbitProvisioner.class);
        assertThat(service(broker).prepare(c).state()).isEqualTo("existing");verifyNoInteractions(broker);
    }
    @ParameterizedTest @ValueSource(strings={"disable","revoke","platform","role","secret"})
    void neverRepairsRevokedOrChangedIdentity(String mutation) {
        var c=channel("max");var broker=mock(ChildRabbitProvisioner.class);var service=service(broker);
        assertThat(service.prepare(c).state()).isEqualTo("ready");clearInvocations(broker);
        switch(mutation) {
            case "disable" -> jdbc.update("UPDATE channels SET is_active=false WHERE id=?",c.getId());
            case "revoke" -> jdbc.update("UPDATE bot_child_principals SET enabled=false WHERE channel_id=?",c.getId());
            case "platform" -> jdbc.update("UPDATE channels SET platform='telegram' WHERE id=?",c.getId());
            case "role" -> jdbc.execute("ALTER ROLE iguana_child_"+c.getId()+" CREATEROLE");
            case "secret" -> service=new ChildInfrastructureService(repository,broker,new MockEnvironment().withProperty("app.integration.transport.mode","rabbitmq").withProperty("app.bots.internal-api.token",MASTER+"changed"));
        }
        assertThat(service.prepare(c).state()).isEqualTo("failed");verifyNoInteractions(broker);
    }
    @Test void migrationPinsFunctionToItsOwnSchema() throws Exception {
        String schema="infra288_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE SCHEMA "+schema);
        var scoped=new JdbcTemplate(new DriverManagerDataSource(url+"?currentSchema="+schema,"infra_test",password));
        scoped.execute("CREATE TABLE channels(id bigint PRIMARY KEY,platform text,is_active boolean)");
        for(String migration:List.of("V24__integration_transport_outbox.sql","V85__outbound_delivery_receipts.sql","V86__child_transport_principals.sql","V94__automatic_child_infrastructure.sql"))scoped.execute(sql(migration));
        long id=ids.incrementAndGet();scoped.update("INSERT INTO channels VALUES(?,'max',true)",id);
        assertThat(new ChildInfrastructureRepository(scoped).prepare(new Principal(id,"max"),"a".repeat(64),ChildRabbitProvisionerTest.HASH).managed()).isTrue();
        assertThat(scoped.queryForObject("SELECT count(*) FROM bot_child_infrastructure WHERE channel_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bot_child_infrastructure WHERE channel_id=?",Integer.class,id)).isZero();
    }
}
