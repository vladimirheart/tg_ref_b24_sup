package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PostgresLegacyCriticalDataRecoverySourceContractTest {

    @Test
    void recoversTablesSkippedByTheGenericImporter() throws IOException {
        Path source = Path.of("src/main/java/com/example/panel/service/PostgresLegacyCriticalDataRecoveryService.java");

        assertThat(Files.readString(source))
            .contains("\"messages\"")
            .contains("\"chat_history\"")
            .contains("\"notifications\"")
            .contains("\"web_form_sessions\"")
            .contains("\"chat_attachment_metadata\"")
            .contains("?mode=ro&immutable=1");
    }
}
