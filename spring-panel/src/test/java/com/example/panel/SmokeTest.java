package com.example.panel;

import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("sqlite")
class SmokeTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void sqliteDatabaseProperties(DynamicPropertyRegistry registry) {
        Path dbFile = tempDir.resolve("panel-smoke.db");
        registry.add("app.datasource.sqlite.path", () -> dbFile.toString());
    }

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @LocalServerPort
    private int port;

    @Test
    void applicationStartsAndSchemaIsReady() {
        assertThat(applicationContext).isNotNull();
        assertThat(port).isPositive();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("admin", "admin")
        );
        assertThat(authentication.isAuthenticated()).isTrue();

        Long userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        assertThat(userCount).isNotNull();
        assertThat(userCount).isEqualTo(1L);

        assertThat(ticketRepository.count()).isZero();
        assertThat(messageRepository.count()).isZero();

        Long statsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM client_stats", Long.class);
        assertThat(statsCount).isNotNull();
        assertThat(statsCount).isEqualTo(0L);
    }
}
