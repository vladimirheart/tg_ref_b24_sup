package com.example.panel.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyTicketIdSupportTest {
    @ParameterizedTest
    @ValueSource(strings = {"T-EXACT", " T-EXACT ", " T-EXACT", "T-EXACT ", "\tT-EXACT\n", "null", " null ", "NULL", "Т-Ё-Exact"})
    void nonblankIdsAreOpaqueAndUnchanged(String id) {
        assertThat(LegacyTicketIdSupport.exactTicketIdOrNull(id)).isSameAs(id);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "\t", "\n", "\u2003"})
    void blankIdsAreAbsent(String id) {
        assertThat(LegacyTicketIdSupport.exactTicketIdOrNull(id)).isNull();
    }
}
