package com.example.panel.service;

import com.example.panel.entity.AutomationRun;
import com.example.panel.entity.AutomationRunItem;
import com.example.panel.repository.AutomationRunRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

/** Isolated real PostgreSQL/JPA acceptance. Requires Docker; never a working datasource. */
class AutomationRunOwnershipPostgresqlTest {
    private JdbcTemplate jdbc;
    private SessionFactory factory;
    private static final String KEY=EmployeeDiscountAutomationService.AUTOMATION_KEY;
    @BeforeEach void schema() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplate("automation_run_owner");
        script("V10__bitrix24_iiko_automation_runs.sql");script("V31__automation_runs_actor_history_index.sql");
        jdbc.update("INSERT INTO automation_runs(id,automation_key,mode,status,actor) VALUES(100,?,'dry_run','success','reader')",KEY);
        script("V83__automation_runs_account_owner.sql");
        var configuration=new Configuration().addAnnotatedClass(AutomationRun.class).addAnnotatedClass(AutomationRunItem.class);
        configuration.setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy());
        configuration.getProperties().put("hibernate.connection.datasource",jdbc.getDataSource());
        configuration.setProperty("hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto","none");
        factory=configuration.buildSessionFactory();
    }
    @AfterEach void close(){if(factory!=null)factory.close();}
    private void script(String name) throws Exception {jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql",name)));}
    private AutomationRun row(Long owner,String actor,String key){var row=new AutomationRun();row.setActorUserId(owner);row.setActor(actor);row.setAutomationKey(key);row.setMode("dry_run");row.setStatus("success");row.setSummary("fixture");row.setStartedAt(OffsetDateTime.parse("2026-10-27T10:00:00Z"));row.setCreatedAt(row.getStartedAt());return row;}
    @Test void upgradeLeavesLegacyRowsUnboundAndKeepsTheOldIndex() {
        assertThat(jdbc.queryForMap("SELECT actor,actor_user_id FROM automation_runs WHERE id=100")).containsEntry("actor","reader").containsEntry("actor_user_id",null);
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname=current_schema() AND tablename='automation_runs'",String.class)).contains("idx_automation_runs_key_actor_started","idx_automation_runs_key_actor_user_started_id");
    }
    @ParameterizedTest @ValueSource(longs={0,-1})
    void invalidNumericOwnersAreRejectedByTheDatabase(long owner) {
        assertThatThrownBy(()->jdbc.update("INSERT INTO automation_runs(automation_key,mode,status,actor_user_id) VALUES(?,'dry_run','success',?)",KEY,owner)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @ParameterizedTest @ValueSource(longs={7L,9007199254740993L,Long.MAX_VALUE})
    void realJpaRoundTripAndDerivedQueriesUseOnlyTheExactNumericOwner(long owner) {
        try(var session=factory.openSession()) {
            var tx=session.beginTransaction();var repository=new JpaRepositoryFactory(session).getRepository(AutomationRunRepository.class);
            var own=repository.save(row(owner," reader ",KEY));var neighbour=repository.save(row(owner==7?99L:7L,"READER",KEY));var other=repository.save(row(owner," reader ","another automation"));tx.commit();session.clear();
            assertThat(repository.findTop20ByAutomationKeyAndActorUserIdOrderByStartedAtDescIdDesc(KEY,owner)).extracting(AutomationRun::getId).containsExactly(own.getId());
            assertThat(repository.findByIdAndAutomationKeyAndActorUserId(own.getId(),KEY,owner)).get().satisfies(saved->{assertThat(saved.getActorUserId()).isEqualTo(owner);assertThat(saved.getActor()).isEqualTo(" reader ");});
            assertThat(repository.findByIdAndAutomationKeyAndActorUserId(neighbour.getId(),KEY,owner)).isEmpty();assertThat(repository.findByIdAndAutomationKeyAndActorUserId(other.getId(),KEY,owner)).isEmpty();assertThat(repository.findByIdAndAutomationKeyAndActorUserId(100L,KEY,owner)).isEmpty();
            jdbc.update("UPDATE automation_runs SET actor='changed display' WHERE id=?",own.getId());session.clear();assertThat(repository.findByIdAndAutomationKeyAndActorUserId(own.getId(),KEY,owner)).get().extracting(AutomationRun::getActor).isEqualTo("changed display");
            assertThat(jdbc.queryForObject("SELECT actor_user_id FROM automation_runs WHERE id=?",Long.class,own.getId())).isEqualTo(owner);
        }
    }
    @Test void realListReturnsOnlyTwentyOwnedRowsInDeterministicOrder() {
        try(var session=factory.openSession()) {
            var tx=session.beginTransaction();var repository=new JpaRepositoryFactory(session).getRepository(AutomationRunRepository.class);
            var ids=new java.util.ArrayList<Long>();for(int i=0;i<25;i++)ids.add(repository.save(row(7L,"reader",KEY)).getId());repository.save(row(99L,"reader",KEY));tx.commit();session.clear();
            var expected=ids.stream().sorted(java.util.Comparator.reverseOrder()).limit(20).toList();assertThat(repository.findTop20ByAutomationKeyAndActorUserIdOrderByStartedAtDescIdDesc(KEY,7L)).extracting(AutomationRun::getId).containsExactlyElementsOf(expected);
        }
    }
    @Test void fullFlywayUpgradeFromV82AddsOnlyV83AndDoesNotInferLegacyOwners() {
        var database=PostgresqlJdbcTestSupport.freshJdbcTemplate("automation_owner_flyway");
        Flyway.configure().dataSource(database.getDataSource()).locations("classpath:db/migration/postgresql").target("82").load().migrate();
        database.update("INSERT INTO automation_runs(automation_key,mode,status,actor) VALUES(?,'dry_run','success','reader')",KEY);
        var flyway=Flyway.configure().dataSource(database.getDataSource()).locations("classpath:db/migration/postgresql").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);flyway.validate();
        assertThat(database.queryForMap("SELECT actor,actor_user_id FROM automation_runs")).containsEntry("actor","reader").containsEntry("actor_user_id",null);assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
