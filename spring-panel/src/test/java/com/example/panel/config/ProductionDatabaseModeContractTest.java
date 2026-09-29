package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionDatabaseModeContractTest {

    @Test
    void postgresqlIsTheOnlyPanelMode() {
        assertThat(DatabaseMode.from(null)).isEqualTo(DatabaseMode.POSTGRESQL);
        assertThat(DatabaseMode.from("postgresql")).isEqualTo(DatabaseMode.POSTGRESQL);
        assertThat(DatabaseMode.from("postgres")).isEqualTo(DatabaseMode.POSTGRESQL);
    }

    @Test
    void legacyAndAutoModesAreRejected() {
        for (String mode : new String[]{"sqlite", "mysql", "auto", "worker"}) {
            assertThatThrownBy(() -> DatabaseMode.from(mode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only postgresql");
        }
    }
}
