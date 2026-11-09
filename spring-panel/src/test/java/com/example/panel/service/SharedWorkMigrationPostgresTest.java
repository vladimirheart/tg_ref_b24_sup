package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class SharedWorkMigrationPostgresTest {
    private Flyway flyway(JdbcTemplate jdbc,String target){
        String schema=jdbc.queryForObject("SELECT current_schema()",String.class);
        return Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration/postgresql").target(target).load();
    }
    @Test void cleanSchemaReachesV88WithoutImplicitGrantsOrParticipants() {
        var jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("shared_clean_v88");
        var flyway=flyway(jdbc,"88");flyway.migrate();flyway.validate();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("88");
        for(String table:java.util.List.of("shared_work_grants","shared_task_members","shared_project_members"))
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class)).isZero();
    }
    @Test void upgradePreservesLegacyRowsAndDoesNotInferOwnerFromCreatorOrLead() {
        var jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("shared_upgrade_v88");
        flyway(jdbc,"87").migrate();
        jdbc.execute("INSERT INTO users(id,username,password) OVERRIDING SYSTEM VALUE VALUES(9007199254740993,' owner ','unused')");
        jdbc.execute("INSERT INTO tasks(id,seq,creator,title) OVERRIDING SYSTEM VALUE VALUES(77,7,' owner ','Legacy task')");
        jdbc.execute("INSERT INTO projects(id,name,lead_identity) OVERRIDING SYSTEM VALUE VALUES(88,'Legacy project',' owner ')");
        jdbc.execute("INSERT INTO task_project_memberships(task_id,project_id) VALUES(77,88)");
        var before=jdbc.queryForList("SELECT task.id,task.seq,task.creator,task.title,project.id project_id,project.name,project.lead_identity FROM tasks task JOIN task_project_memberships link ON link.task_id=task.id JOIN projects project ON project.id=link.project_id");
        var flyway=flyway(jdbc,"88");assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);flyway.validate();
        assertThat(jdbc.queryForList("SELECT task.id,task.seq,task.creator,task.title,project.id project_id,project.name,project.lead_identity FROM tasks task JOIN task_project_memberships link ON link.task_id=task.id JOIN projects project ON project.id=link.project_id")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT shared_owner_user_id FROM tasks WHERE id=77",Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT shared_owner_user_id FROM projects WHERE id=88",Long.class)).isNull();
        for(String table:java.util.List.of("shared_work_grants","shared_task_members","shared_project_members"))
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class)).isZero();
    }
}
