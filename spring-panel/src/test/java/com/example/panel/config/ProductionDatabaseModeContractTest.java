package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionDatabaseModeContractTest {

    @Test
    void postgresqlRemainsTheCanonicalPanelMode() {
        assertThat(DatabaseMode.from("postgresql")).isEqualTo(DatabaseMode.POSTGRESQL);
        assertThat(DatabaseMode.from("postgres")).isEqualTo(DatabaseMode.POSTGRESQL);
    }

    @Test
    void explicitSqlitePanelModeIsRetired() {
        assertThatThrownBy(() -> DatabaseMode.from("sqlite"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SQLite runtime mode was retired");
    }
}
