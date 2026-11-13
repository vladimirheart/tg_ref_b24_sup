package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class ChannelTransportStatePostgresTest {
    private static final long ID = 9_007_199_254_740_993L;
    private static PostgreSQLContainer<?> postgres;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static EntityManagerFactory emf;
    private static EntityManager managed;
    private static JpaTransactionManager transactions;
    private static ChannelRepository channels;
    private static JdbcTemplate independentJdbc;

    @BeforeAll static void database() {
        String url = System.getenv("PANEL_CHANNEL_STATE_TEST_JDBC_URL");
        String user = "r257_test", password = "r257-test-only";
        if (url == null || url.isBlank()) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("r257_test").withUsername(user).withPassword(password)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                    .withMemory(192L * 1024 * 1024).withNanoCPUs(1_000_000_000L));
            postgres.start();
            url = postgres.getJdbcUrl();
        } else {
            // Explicit isolated test endpoint only: never run create/drop against runtime DB.
            assertThat(url).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r257_test");
        }
        var datasource = new DriverManagerDataSource(url, user, password);
        // Distinct DataSource identity prevents JdbcTemplate from joining the JPA transaction.
        independentJdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(datasource);
        factory.setManagedTypes(PersistenceManagedTypes.of(Channel.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
            "hibernate.hbm2ddl.auto", "create-drop",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
            "hibernate.show_sql", "false"));
        factory.afterPropertiesSet();
        emf = factory.getObject();
        managed = SharedEntityManagerCreator.createSharedEntityManager(emf);
        transactions = new JpaTransactionManager(emf);
        var repository = new JpaRepositoryFactory(managed).getRepository(ChannelRepository.class);
        var proxy = new ProxyFactory(repository);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        channels = (ChannelRepository) proxy.getProxy();
    }

    @AfterAll static void cleanup() {
        try { if (factory != null) factory.destroy(); }
        finally { if (postgres != null) postgres.stop(); }
    }

    @BeforeEach void row() {
        independentJdbc.execute("TRUNCATE TABLE channels");
        independentJdbc.update("""
            INSERT INTO channels(id, token, channel_name, platform, is_active, support_chat_id,
                                 delivery_settings, platform_config)
            VALUES (?, 'test-inline', 'isolated test', 'telegram', TRUE, '-123', ?, ?)
            """, ID, "{\"broadcast_channel_id\":\"-456\"}", "{\"bot_api_base_url\":\"https://api.telegram.org\"}");
    }

    @Test void scalarReadSeesCommittedChangeOutsideCachedRepeatableReadTransaction() {
        var outer = new TransactionTemplate(transactions);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        outer.executeWithoutResult(status -> {
            Channel cached = managed.find(Channel.class, ID);
            assertThat(cached.getActive()).isTrue();
            assertThat(channels.findTransportStateById(ID).orElseThrow().getActive()).isTrue();
            independentJdbc.update("UPDATE channels SET is_active=FALSE WHERE id=?", ID);
            assertThat(managed.find(Channel.class, ID)).isSameAs(cached);
            assertThat(cached.getActive()).isTrue();
            assertThat(channels.findTransportStateById(ID).orElseThrow().getActive()).isFalse();
            assertThat(cached.getActive()).isTrue(); // Inner read did not mutate/refresh caller's entity.
        });
    }

    @Test void projectionPreservesBigintAndNullableFields() {
        independentJdbc.update("""
            UPDATE channels SET credential_id=?, is_active=NULL, support_chat_id=NULL,
                                delivery_settings=NULL, platform_config=NULL WHERE id=?
            """, ID + 10, ID);
        var state = channels.findTransportStateById(ID).orElseThrow();
        assertThat(state.getId()).isEqualTo(ID);
        assertThat(state.getCredentialId()).isEqualTo(ID + 10);
        assertThat(state.getActive()).isNull();
        assertThat(state.getSupportChatId()).isNull();
        assertThat(state.getDeliverySettings()).isNull();
        assertThat(state.getPlatformConfig()).isNull();
    }

    @Test void priorProjectionDoesNotFollowLaterDatabaseMutation() {
        var before = channels.findTransportStateById(ID).orElseThrow();
        independentJdbc.update("UPDATE channels SET support_chat_id='-999' WHERE id=?", ID);
        var after = channels.findTransportStateById(ID).orElseThrow();
        assertThat(before.getSupportChatId()).isEqualTo("-123");
        assertThat(after.getSupportChatId()).isEqualTo("-999");
        assertThat(channels.findTransportStateById(ID + 1)).isEmpty();
    }

    static Stream<Arguments> deliveryChanges() {
        return Stream.of("test", "support_chat", "broadcast_channel", "custom_chat").flatMap(op ->
            Stream.of("none", "recipient", "inactive", "token", "deleted", "api-root", "network-route", "rebind")
                .map(change -> Arguments.of(op, change)));
    }

    @ParameterizedTest @MethodSource("deliveryChanges")
    void realQueryGatesProviderAfterIndependentCommitInsideNetworkSetup(String operation, String change) throws Exception {
        var config = mock(SharedConfigService.class);
        var network = mock(IntegrationNetworkService.class);
        var http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"ok\":true}");
        doReturn(response).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(network.resolveTelegramLegacyBotApiBaseUrl(any())).thenReturn("https://api.telegram.org");
        when(network.createChannelHttpClient(any(), any())).thenAnswer(call -> {
            switch (change) {
                case "none" -> { }
                case "recipient" -> independentJdbc.update("UPDATE channels SET support_chat_id='-999' WHERE id=?", ID);
                case "inactive" -> independentJdbc.update("UPDATE channels SET is_active=FALSE WHERE id=?", ID);
                case "token" -> independentJdbc.update("UPDATE channels SET token='different' WHERE id=?", ID);
                case "deleted" -> independentJdbc.update("DELETE FROM channels WHERE id=?", ID);
                case "api-root" -> independentJdbc.update("UPDATE channels SET platform_config='{}' WHERE id=?", ID);
                case "network-route" -> independentJdbc.update("UPDATE channels SET delivery_settings='{}' WHERE id=?", ID);
                case "rebind" -> independentJdbc.update("UPDATE channels SET credential_id=7 WHERE id=?", ID);
                default -> throw new AssertionError(change);
            }
            return http;
        });
        var service = new ChannelTransportService(channels, new ObjectMapper(), config, network, org.mockito.Mockito.mock(com.example.panel.service.ChildInfrastructureService.class));
        var outer = new TransactionTemplate(transactions);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        boolean sent = outer.execute(status -> {
            Channel cached = managed.find(Channel.class, ID);
            boolean result = operation.equals("test")
                ? Boolean.TRUE.equals(service.testChannel(ID, Map.of("target_mode", "group", "message", "isolated test")).getBody().get("success"))
                : service.sendConfiguredMessage(ID, operation, "777", "isolated test").success();
            assertThat(managed.find(Channel.class, ID)).isSameAs(cached);
            assertThat(cached.getActive()).isTrue();
            assertThat(cached.getSupportChatId()).isEqualTo("-123");
            return result;
        });
        assertThat(sent).isEqualTo(change.equals("none"));
        verify(network).createChannelHttpClient(any(), any());
        verify(http, times(change.equals("none") ? 1 : 0)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
