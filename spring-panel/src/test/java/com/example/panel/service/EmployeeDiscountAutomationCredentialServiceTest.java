package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EmployeeDiscountAutomationCredentialServiceTest {

    @Test
    void postgresqlBooleanContractPersistsLoadsAndAllowsClearingAllSelections() throws Exception {
        JdbcTemplate jdbcTemplate = postgresqlModeJdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        EmployeeDiscountAutomationCredentialService service =
            new EmployeeDiscountAutomationCredentialService(jdbcTemplate, objectMapper);

        service.saveForUser("alice", Map.of(
            "bitrix24", Map.of(
                "portal_url", "https://portal.example",
                "webhook_url", "https://portal.example/rest/1/secret-webhook"
            ),
            "iiko_profile", Map.of(
                "base_url", "https://iiko.example",
                "api_login", "login",
                "api_secret", "secret-iiko",
                "organization_id", "org-1",
                "selected_discount_category_ids", List.of("cat-1"),
                "selected_wallet_ids", List.of("wallet-1")
            ),
            "select_profile_url", "https://iiko.example"
        ));

        Map<String, Object> clientView = service.loadClientView("alice");
        assertThat(clientView.toString())
            .doesNotContain("secret-webhook")
            .doesNotContain("secret-iiko")
            .contains("webhook_saved=true")
            .contains("api_secret_saved=true")
            .contains("cat-1")
            .contains("wallet-1");

        service.saveForUser("alice", Map.of(
            "iiko_profile", Map.of(
                "base_url", "https://iiko.example",
                "api_secret", "",
                "selected_discount_category_ids", List.of(),
                "selected_wallet_ids", List.of()
            ),
            "select_profile_url", "https://iiko.example"
        ));

        Map<String, Object> clearedView = service.loadClientView("alice");
        assertThat(clearedView.toString())
            .contains("api_secret_saved=true")
            .contains("selected_discount_category_ids=[]")
            .contains("selected_wallet_ids=[]")
            .doesNotContain("cat-1")
            .doesNotContain("wallet-1")
            .doesNotContain("secret-iiko");

        String storedJson = jdbcTemplate.queryForObject(
            "SELECT extra_json FROM settings_parameters WHERE param_type = ? AND value = ? AND is_deleted = FALSE",
            String.class,
            "employee_discount_automation_credentials.v1",
            "alice"
        );
        Map<String, Object> stored = objectMapper.readValue(storedJson, new TypeReference<LinkedHashMap<String, Object>>() {});
        assertThat(stored.toString()).contains("secret-webhook").contains("secret-iiko");
    }

    @Test
    void loadFailsClosedWhenCredentialStoreIsUnavailable() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate() {
            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                throw new DataAccessResourceFailureException("database unavailable");
            }
        };
        EmployeeDiscountAutomationCredentialService service =
            new EmployeeDiscountAutomationCredentialService(jdbcTemplate, new ObjectMapper());

        assertThatThrownBy(() -> service.loadForUser("alice"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to load employee discount credentials");
    }

    @Test
    void saveFailsClosedWhenDatabaseWriteIsNotPersisted() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate() {
            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                return List.of();
            }

            @Override
            public int update(String sql, Object... args) {
                throw new DataAccessResourceFailureException("write unavailable");
            }
        };
        EmployeeDiscountAutomationCredentialService service =
            new EmployeeDiscountAutomationCredentialService(jdbcTemplate, new ObjectMapper());

        assertThatThrownBy(() -> service.saveForUser("alice", Map.of(
            "bitrix24", Map.of(
                "portal_url", "https://portal.example",
                "webhook_url", "https://portal.example/rest/1/secret-token"
            )
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to persist employee discount credentials");
    }

    private JdbcTemplate postgresqlModeJdbcTemplate() {
        String databaseName = "employee_discount_credentials_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
            "sa",
            ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
            CREATE TABLE settings_parameters (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                param_type VARCHAR(255) NOT NULL,
                value VARCHAR(255) NOT NULL,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                state VARCHAR(64) NOT NULL DEFAULT 'Активен',
                is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                deleted_at TIMESTAMP WITH TIME ZONE,
                extra_json VARCHAR(20000),
                UNIQUE(param_type, value)
            )
            """);
        return jdbcTemplate;
    }
}