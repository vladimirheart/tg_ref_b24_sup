package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class EmployeeDiscountAutomationCredentialServiceTest {
    private static final org.springframework.security.core.Authentication ALICE = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
        new com.example.panel.security.PanelUserPrincipal(7L,"alice","",true,List.of()),null,List.of());
    private javax.sql.DataSource transactionDataSource() throws Exception {
        var ds=org.mockito.Mockito.mock(javax.sql.DataSource.class);var connection=org.mockito.Mockito.mock(java.sql.Connection.class);
        org.mockito.Mockito.when(ds.getConnection()).thenReturn(connection);org.mockito.Mockito.when(connection.getAutoCommit()).thenReturn(true);return ds;
    }


    @Test
    void postgresqlBooleanContractPersistsLoadsAndAllowsClearingAllSelections() throws Exception {
        JdbcTemplate jdbcTemplate = postgresqlModeJdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        EmployeeDiscountAutomationCredentialService service =
            new EmployeeDiscountAutomationCredentialService(jdbcTemplate, objectMapper);

        service.saveForUser(ALICE, Map.of(
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

        Map<String, Object> clientView = service.loadClientView(ALICE);
        assertThat(clientView.toString())
            .doesNotContain("secret-webhook")
            .doesNotContain("secret-iiko")
            .contains("webhook_saved=true")
            .contains("api_secret_saved=true")
            .contains("cat-1")
            .contains("wallet-1");

        service.saveForUser(ALICE, Map.of(
            "iiko_profile", Map.of(
                "base_url", "https://iiko.example",
                "api_secret", "",
                "selected_discount_category_ids", List.of(),
                "selected_wallet_ids", List.of()
            ),
            "select_profile_url", "https://iiko.example"
        ));

        Map<String, Object> clearedView = service.loadClientView(ALICE);
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
            "employee_discount_automation_credentials.account.v2",
            "7"
        );
        Map<String, Object> stored = objectMapper.readValue(storedJson, new TypeReference<LinkedHashMap<String, Object>>() {});
        assertThat(stored.toString()).contains("secret-webhook").contains("secret-iiko");
    }

    @Test
    void loadFailsClosedWhenCredentialStoreIsUnavailable() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(transactionDataSource()) {
            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                throw new DataAccessResourceFailureException("database unavailable");
            }
        };
        EmployeeDiscountAutomationCredentialService service =
            new EmployeeDiscountAutomationCredentialService(jdbcTemplate, new ObjectMapper());

        assertThatThrownBy(() -> service.loadForUser(ALICE))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Credential storage is unavailable");
    }

    @Test
    void saveFailsClosedWhenDatabaseWriteIsNotPersisted() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(transactionDataSource()) {
            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                if (sql.contains("FROM users")) return List.of(Map.of("id",7L,"username","alice","enabled",true,"is_blocked",false));
                return List.of();
            }

            @Override
            public int update(String sql, Object... args) {
                throw new DataAccessResourceFailureException("write unavailable");
            }
        };
        EmployeeDiscountAutomationCredentialService service =
            new EmployeeDiscountAutomationCredentialService(jdbcTemplate, new ObjectMapper());

        assertThatThrownBy(() -> service.saveForUser(ALICE, Map.of(
            "bitrix24", Map.of(
                "portal_url", "https://portal.example",
                "webhook_url", "https://portal.example/rest/1/secret-token"
            )
        )))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Credential storage is unavailable");
    }

    private JdbcTemplate postgresqlModeJdbcTemplate() {
        JdbcTemplate jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("employee_discount_credentials");
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
        jdbcTemplate.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN,is_blocked BOOLEAN)");
        jdbcTemplate.update("INSERT INTO users VALUES(7,'alice',TRUE,FALSE)");
        return jdbcTemplate;
    }
}