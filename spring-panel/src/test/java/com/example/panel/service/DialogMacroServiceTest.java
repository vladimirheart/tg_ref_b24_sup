package com.example.panel.service;

import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DialogMacroServiceTest {

    private DialogDetailsReadService details;
    private DialogClientContextReadService profile;
    private SharedConfigService config;
    private DialogMacroService service;

    @BeforeEach
    void setUp() {
        details = mock(DialogDetailsReadService.class);
        profile = mock(DialogClientContextReadService.class);
        config = mock(SharedConfigService.class);
        when(config.loadSettings()).thenReturn(Map.of(
            "dialog_config", Map.of("macro_variable_defaults", Map.of("organization", " Support "))));
        service = new DialogMacroService(details, profile, config);
    }

    @ParameterizedTest
    @ValueSource(strings = {" T-EXACT", "T-EXACT ", " T-EXACT "})
    void dryRunAndCatalogKeepExactContextSeparateFromPlainNeighbour(String ticketId) {
        stubContext("T-EXACT", 88L, "plain client");
        stubContext(ticketId, 77L, "exact client");
        when(profile.loadClientProfileEnrichment(77L)).thenReturn(Map.of(
            "total_dialogs", 3, "custom_label", "exact profile"));
        when(profile.loadClientProfileEnrichment(88L)).thenReturn(Map.of(
            "total_dialogs", 99, "custom_label", "foreign profile"));

        DialogMacroService.MacroDryRunResponse rendered = service.dryRun(ticketId,
            "ID=[{{ticket_id}}]; {{client_name}}; {{client_total_dialogs}}; {{client_custom_label}}",
            "operator", Map.of());
        List<Map<String, String>> catalog = service.loadVariables(ticketId, "operator");

        assertThat(rendered.renderedText()).isEqualTo(
            "ID=[" + ticketId + "]; exact client; 3; exact profile");
        assertThat(rendered.missingVariables()).isEmpty();
        assertThat(catalog).contains(Map.of("key", "client_custom_label", "label", "Client Custom Label",
            "default_value", "exact profile", "source", "ticket_context"));
        verify(details, times(2)).loadDialogDetails(ticketId, null, "operator");
        verify(profile, times(2)).loadClientProfileEnrichment(77L);
        verifyNoMoreInteractions(details, profile);
    }

    @ParameterizedTest
    @ValueSource(strings = {" T-PLAIN", "T-PLAIN ", " T-PLAIN ", "T-MISSING", "T-DUP"})
    void unresolvedIdDoesNotBorrowPlainNeighboursClientContext(String ticketId) {
        stubContext("T-PLAIN", 88L, "private client");
        when(profile.loadClientProfileEnrichment(88L)).thenReturn(Map.of("secret", "private profile"));

        DialogMacroService.MacroDryRunResponse rendered = service.dryRun(ticketId,
            "ID=[{{ticket_id}}]; {{client_name|none}}; {{client_secret|none}}", "operator", Map.of());
        List<Map<String, String>> catalog = service.loadVariables(ticketId, "operator");

        assertThat(rendered.renderedText()).isEqualTo("ID=[" + ticketId + "]; none; none");
        assertThat(rendered.missingVariables()).containsExactly("client_name", "client_secret");
        assertThat(catalog).noneMatch(item -> "ticket_context".equals(item.get("source")));
        verify(details, times(2)).loadDialogDetails(ticketId, null, "operator");
        verifyNoInteractions(profile);
        verifyNoMoreInteractions(details);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" "})
    void macroWithoutTicketRetainsGlobalDefaultsAndSkipsContextLookup(String ticketId) {
        DialogMacroService.MacroDryRunResponse rendered = service.dryRun(ticketId,
            "{{ticket_id}} / {{operator_name}} / {{organization}} / {{client_name|none}}",
            "operator", Map.of());
        List<Map<String, String>> catalog = service.loadVariables(ticketId, "operator");

        assertThat(rendered.renderedText()).isEqualTo("— / operator / Support / none");
        assertThat(rendered.missingVariables()).containsExactly("client_name");
        assertThat(catalog).contains(Map.of("key", "organization", "label", "Значение из dialog_config",
            "default_value", "Support", "source", "dialog_config_default"));
        verifyNoInteractions(details, profile);
    }

    @Test
    void plainIdStillLoadsItsExistingContext() {
        stubContext("T-PLAIN", 88L, "plain client");

        DialogMacroService.MacroDryRunResponse rendered = service.dryRun("T-PLAIN",
            "{{ticket_id}} / {{client_name}}", "operator", Map.of());

        assertThat(rendered.renderedText()).isEqualTo("T-PLAIN / plain client");
        verify(details).loadDialogDetails("T-PLAIN", null, "operator");
        verify(profile).loadClientProfileEnrichment(88L);
    }

    @Test
    void requestOverridesStillTakePrecedenceOverExactTicketAndConfigDefaults() {
        stubContext(" T-EXACT ", 77L, "exact client");

        DialogMacroService.MacroDryRunResponse rendered = service.dryRun(" T-EXACT ",
            "{{ticket_id}} / {{client_name}} / {{organization}}", "operator",
            Map.of("ticket_id", "override ID", " CLIENT_NAME ", "override client", "organization", "override org"));

        assertThat(rendered.renderedText()).isEqualTo("override ID / override client / override org");
        assertThat(rendered.missingVariables()).isEmpty();
        verify(details).loadDialogDetails(" T-EXACT ", null, "operator");
    }

    @Test
    void unresolvedTicketStillAllowsExplicitPreviewVariablesWithoutProfileLookup() {
        DialogMacroService.MacroDryRunResponse rendered = service.dryRun(" T-MISSING ",
            "{{client_name}} / {{organization}}", "operator", Map.of("client_name", "preview client"));

        assertThat(rendered.renderedText()).isEqualTo("preview client / Support");
        verify(details).loadDialogDetails(" T-MISSING ", null, "operator");
        verifyNoInteractions(profile);
    }

    @ParameterizedTest
    @ValueSource(strings = {"T-MISSING", "T-DUP", " T-PLAIN "})
    void realPostgresGuardStopsMacroBeforeChildReadsForUnresolvedExactId(String ticketId) {
        JdbcTemplate rootJdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("macro_ticket_guard");
        rootJdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, PRIMARY KEY(user_id, ticket_id))");
        rootJdbc.update("INSERT INTO tickets VALUES (1, 'T-PLAIN'), (2, 'T-DUP'), (3, 'T-DUP')");
        LegacyTicketIdJdbcGuard guard = spy(new LegacyTicketIdJdbcGuard(rootJdbc));
        JdbcTemplate panelJdbc = mock(JdbcTemplate.class);
        JdbcTemplate usersJdbc = mock(JdbcTemplate.class);
        DialogLookupReadService lookup = new DialogLookupReadService(panelJdbc, usersJdbc,
            mock(PanelUserPhotoService.class), new PanelTimestampSqlSupport(), guard);
        DialogConversationReadService conversation = mock(DialogConversationReadService.class);
        DialogMacroService guardedService = new DialogMacroService(
            new DialogDetailsReadService(lookup, conversation), profile, config);

        DialogMacroService.MacroDryRunResponse rendered = guardedService.dryRun(ticketId,
            "ID=[{{ticket_id}}]; {{client_name|none}}", "operator", Map.of());
        List<Map<String, String>> catalog = guardedService.loadVariables(ticketId, "operator");

        assertThat(rendered.renderedText()).isEqualTo("ID=[" + ticketId + "]; none");
        assertThat(catalog).noneMatch(item -> "ticket_context".equals(item.get("source")));
        verify(guard, times(2)).hasUniqueTicket(ticketId);
        verifyNoInteractions(panelJdbc, usersJdbc, conversation, profile);
    }

    private void stubContext(String ticketId, long userId, String clientName) {
        DialogListItem summary = new DialogListItem(
            ticketId, 1L, userId, "client" + userId, clientName, "legacy label", 9L, "Telegram",
            "City", "Office", "problem", "2026-10-11T10:00:00Z", "open", false,
            null, "operator", "11.10.2026", "13:00:00", null, "client",
            "2026-10-11T10:01:00Z", 0, null, null);
        when(details.loadDialogDetails(ticketId, null, "operator"))
            .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
    }
}
