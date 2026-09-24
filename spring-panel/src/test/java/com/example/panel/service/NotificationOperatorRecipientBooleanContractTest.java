package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NotificationOperatorRecipientBooleanContractTest {

    @Test
    void booleanFlagsRemainPortableAcrossPostgresAndLegacyNumericSchemas() {
        assertThat(NotificationService.coerceBooleanFlag(Boolean.TRUE, false)).isTrue();
        assertThat(NotificationService.coerceBooleanFlag(Boolean.FALSE, true)).isFalse();
        assertThat(NotificationService.coerceBooleanFlag(1, false)).isTrue();
        assertThat(NotificationService.coerceBooleanFlag(0, true)).isFalse();
        assertThat(NotificationService.coerceBooleanFlag("true", false)).isTrue();
        assertThat(NotificationService.coerceBooleanFlag("0", true)).isFalse();
        assertThat(NotificationService.coerceBooleanFlag(null, true)).isTrue();
        assertThat(NotificationService.coerceBooleanFlag(null, false)).isFalse();
    }

    @Test
    void operatorLookupDoesNotMixBooleanColumnsWithNumericSqlLiterals() throws IOException {
        String source = Files.readString(
            Path.of("src/main/java/com/example/panel/service/NotificationService.java"),
            StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace("\r", "\n");

        assertThat(source)
            .contains("rs.getObject(\"enabled\")")
            .contains("rs.getObject(\"is_blocked\")")
            .contains("coerceBooleanFlag")
            .doesNotContain("COALESCE(enabled, 1)")
            .doesNotContain("COALESCE(is_blocked, 0)");
    }
}
