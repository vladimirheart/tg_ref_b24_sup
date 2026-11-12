package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDisplayNamesMigrationTest {
    @Test
    void onlyOriginalSeedNamesChangeWhileIdentityBindingsAndCustomNamesRemain() throws Exception {
        var jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("business_names_v93");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY, code TEXT UNIQUE NOT NULL, display_name TEXT NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT '2020-01-01 00:00:00+00')");
        jdbc.execute("CREATE TABLE bindings(business_id BIGINT REFERENCES businesses(id), value TEXT)");
        jdbc.update("INSERT INTO businesses(id,code,display_name) VALUES (1,'sushi','Суши'),(2,'bliny','Блины'),(3,'other','Суши')");
        jdbc.update("INSERT INTO bindings VALUES (1,'template-a'),(2,'template-b')");
        String migration = Files.readString(Path.of("src/main/resources/db/migration/postgresql/V93__business_display_names.sql"));
        jdbc.execute(migration);
        assertThat(jdbc.queryForList("SELECT id, code, display_name FROM businesses ORDER BY id")).containsExactly(
                java.util.Map.of("id", 1L, "code", "sushi", "display_name", "СушиВёсла"),
                java.util.Map.of("id", 2L, "code", "bliny", "display_name", "Блинбери"),
                java.util.Map.of("id", 3L, "code", "other", "display_name", "Суши"));
        assertThat(jdbc.queryForList("SELECT business_id FROM bindings ORDER BY business_id", Long.class)).containsExactly(1L, 2L);
        var after = jdbc.queryForList("SELECT * FROM businesses ORDER BY id");
        jdbc.execute(migration);
        assertThat(jdbc.queryForList("SELECT * FROM businesses ORDER BY id")).isEqualTo(after);
        jdbc.update("UPDATE businesses SET display_name='Моё название' WHERE id=1");
        jdbc.update("UPDATE businesses SET display_name='Другое название' WHERE id=2");
        jdbc.execute(migration);
        assertThat(jdbc.queryForList("SELECT display_name FROM businesses ORDER BY id", String.class))
                .containsExactly("Моё название", "Другое название", "Суши");
    }
}
