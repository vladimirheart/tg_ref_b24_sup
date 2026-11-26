package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import com.zaxxer.hikari.HikariDataSource;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.*;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.*;

/** Native isolated PG: real OSIV/JPA -> independent session transaction, beyond pool concurrency. */
@EnabledIfSystemProperty(named="r326.jdbc",matches="jdbc:postgresql://127.0.0.1:.*?/r326_test")
class JdbcPoolReleasePostgresTest {
    @Test void notificationJitIsLocalToTheReadTransaction() {
        try(var ds=new HikariDataSource()){
            ds.setJdbcUrl(System.getProperty("r326.jdbc"));ds.setUsername("r326_test");ds.setMaximumPoolSize(1);
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
            assertThat(jdbc.queryForObject("SELECT nonce FROM r326_owner",String.class)).isEqualTo(System.getProperty("r326.nonce"));
            var repository=org.mockito.Mockito.mock(com.example.panel.repository.NotificationRepository.class);
            org.mockito.Mockito.when(repository.findVisiblePage(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation->{assertThat(jdbc.queryForObject("SHOW jit",String.class)).isEqualTo("off");return List.of();});
            var service=new com.example.panel.service.NotificationService(repository,jdbc,jdbc,
                org.mockito.Mockito.mock(com.example.panel.service.UiEventStreamService.class),
                org.mockito.Mockito.mock(com.example.panel.service.LegacyTicketIdJdbcGuard.class));
            assertThat(jdbc.queryForObject("SHOW jit",String.class)).isEqualTo("on");
            new TransactionTemplate(new DataSourceTransactionManager(ds)).execute(s->service.findPageForUser("operator",null,100));
            assertThat(jdbc.queryForObject("SHOW jit",String.class)).isEqualTo("on");
        }
    }
    @Test void concurrentRequestsReleaseBeforeSessionSaveWithOpenEntityManagers() throws Exception {
        var yaml=new YamlPropertiesFactoryBean();yaml.setResources(new ClassPathResource("application.yml"));
        String mode=yaml.getObject().getProperty("spring.jpa.properties.hibernate.connection.handling_mode");
        assertThat(mode).isEqualTo("DELAYED_ACQUISITION_AND_HOLD");
        for(int poolSize:new int[]{6,15})try(var ds=new HikariDataSource()){
            ds.setJdbcUrl(System.getProperty("r326.jdbc"));ds.setUsername("r326_test");
            ds.setMaximumPoolSize(poolSize);ds.setMinimumIdle(0);ds.setConnectionTimeout(5000);
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
            assertThat(jdbc.queryForObject("SELECT nonce FROM r326_owner",String.class)).isEqualTo(System.getProperty("r326.nonce"));
            if(jdbc.queryForObject("SELECT to_regclass('spring_session') IS NULL",Boolean.class))
                new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                    new ClassPathResource("org/springframework/session/jdbc/schema-postgresql.sql")).execute(ds);
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setPackagesToScan("r326.empty");
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.connection.handling_mode",mode));
            factory.afterPropertiesSet();var emf=factory.getObject();
            try{
                var manager=(JpaTransactionManager)new PanelDataSourceConfiguration().transactionManager(emf);
                var session=new TransactionTemplate(new DataSourceTransactionManager(ds));
                session.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
                @SuppressWarnings({"unchecked","rawtypes"})
                org.springframework.session.SessionRepository<org.springframework.session.Session> sessions=
                    (org.springframework.session.SessionRepository)new org.springframework.session.jdbc.JdbcIndexedSessionRepository(jdbc,session);
                int requests=20;var barrier=new CyclicBarrier(requests);
                var executor=Executors.newFixedThreadPool(requests);var futures=new ArrayList<Future<?>>();
                try{
                    for(int n=0;n<requests;n++){
                      final boolean repeatable=n%2==0;
                      futures.add(executor.submit(()->{
                        var em=emf.createEntityManager();
                        TransactionSynchronizationManager.bindResource(emf,new EntityManagerHolder(em));
                        try{
                            var application=new TransactionTemplate(manager);
                            application.setIsolationLevel(repeatable?java.sql.Connection.TRANSACTION_REPEATABLE_READ:java.sql.Connection.TRANSACTION_READ_COMMITTED);
                            application.setReadOnly(repeatable);
                            application.execute(s->{
                                assertThat(em.createNativeQuery("SHOW transaction_isolation").getSingleResult())
                                    .isEqualTo(repeatable?"repeatable read":"read committed");
                                return null;
                            });
                            barrier.await(10,TimeUnit.SECONDS);
                            var stored=sessions.createSession();stored.setAttribute("operator","fixture");
                            sessions.save(stored);
                            assertThat(sessions.findById(stored.getId()).getAttribute("operator").toString()).isEqualTo("fixture");
                            sessions.deleteById(stored.getId());
                        }catch(Exception failure){throw new RuntimeException(failure);}
                        finally{TransactionSynchronizationManager.unbindResource(emf);em.close();}
                      }));
                    }
                    for(var future:futures)future.get(20,TimeUnit.SECONDS);
                }finally{executor.shutdownNow();}
                assertThat(ds.getHikariPoolMXBean().getActiveConnections()).isZero();
                assertThat(ds.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
                try(var connection=ds.getConnection()){
                    assertThat(connection.getTransactionIsolation()).isEqualTo(java.sql.Connection.TRANSACTION_READ_COMMITTED);
                    assertThat(connection.isReadOnly()).isFalse();
                }
            }finally{factory.destroy();}
        }
    }
}
