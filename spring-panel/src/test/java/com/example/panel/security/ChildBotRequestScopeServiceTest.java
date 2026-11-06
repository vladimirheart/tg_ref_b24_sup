package com.example.panel.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

class ChildBotRequestScopeServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final ChildBotRequestScopeService service=new ChildBotRequestScopeService(jdbc,new ObjectMapper());
    private final ChildBotCredentials.Principal child=new ChildBotCredentials.Principal(22,"max");
    @BeforeEach void setup() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call -> {
            String sql=call.getArgument(0);
            return sql.contains("FROM channels") ? List.of(Map.of("id",22L)) : List.of(Map.of("channel_id",22L));
        });
    }
    @AfterEach void teardown() { TransactionSynchronizationManager.clear(); }
    private MockHttpServletRequest request(String method,String pattern,Map<String,String> vars) {
        var request=new MockHttpServletRequest(method,pattern);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,pattern);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,vars);
        return request;
    }
    private void authorize(MockHttpServletRequest request,String json) { service.authorize(request,child,json.getBytes(StandardCharsets.UTF_8)); }
    @ParameterizedTest @ValueSource(strings={"/runtime/channels/{channelId}/start","/runtime/channels/{channelId}/stop","/future-route","/channels/{channelId}/support-chat","/channels/{channelId}/messages/{telegramMessageId}/client-edit"})
    void privilegedUnknownAndUnownedRoutesAreDenied(String route) {
        assertThatThrownBy(() -> authorize(request("PUT","/internal/api/bot"+route,Map.of("channelId","22")),"{}"))
            .isInstanceOf(ResponseStatusException.class);
    }
    @ParameterizedTest @ValueSource(strings={"23","0","022"," 22","9223372036854775808"})
    void routeChannelCannotChooseAnotherPrincipal(String channel) {
        assertThatThrownBy(() -> authorize(request("GET","/internal/api/bot/channels/{channelId}",Map.of("channelId",channel)),"{}"))
            .isInstanceOf(ResponseStatusException.class);
    }
    @ParameterizedTest @ValueSource(strings={"{\"platform\":\"vk\"}","{\"channelId\":23}","{\"channelId\":22.0}","{\"channelId\":\"22\"}","null","[]"})
    void bodyCannotOverrideAuthenticatedScope(String json) {
        assertThatThrownBy(() -> authorize(request("PUT","/internal/api/bot/channels/{channelId}/users/{userId}/messages/{telegramMessageId}/client-edit",Map.of("channelId","22")),json))
            .isInstanceOf(ResponseStatusException.class);
    }
    @ParameterizedTest @ValueSource(strings={"/channels/{channelId}","/channels/{channelId}/runtime-config","/channels/{channelId}/questionnaire/v4/choices","/channels/{channelId}/general/runtime-config"})
    void ownedChannelReadCanProceed(String route) {
        var request=request("GET","/internal/api/bot"+route,Map.of("channelId","22"));
        authorize(request,"{}"); assertThat(ChildBotRequestScopeService.channelId(request)).isEqualTo(22L);
    }
    @Test void rootBelongingToAnotherChannelIsDeniedBeforeServiceAction() {
        when(jdbc.queryForList(eq("SELECT channel_id FROM tickets WHERE ticket_id=? FOR UPDATE"),any(Object[].class)))
            .thenReturn(List.of(Map.of("channel_id",23L)));
        assertThatThrownBy(() -> authorize(request("GET","/internal/api/bot/tickets/{ticketId}",Map.of("ticketId","raw / Т-1 ")),"{}"))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void repeatedQueryChannelIsDenied() {
        var request=request("GET","/internal/api/bot/channels/{channelId}",Map.of("channelId","22"));
        request.addParameter("channelId","22","23");
        assertThatThrownBy(() -> authorize(request,"{}")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void scopeCheckCannotRunOutsideOwningTransaction() {
        TransactionSynchronizationManager.clear();
        assertThatThrownBy(() -> authorize(request("GET","/internal/api/bot/channels/{channelId}",Map.of("channelId","22")),"{}"))
            .isInstanceOf(ResponseStatusException.class); verifyNoInteractions(jdbc);
    }
    @Test void activeLookupRequiresExplicitAuthenticatedChannel() {
        var request=request("GET","/internal/api/bot/tickets/active",Map.of()); request.addParameter("channelId","22");
        authorize(request,"{}");
        request.removeParameter("channelId"); assertThatThrownBy(() -> authorize(request,"{}")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void revokedPrincipalIsDeniedOnEveryRequest() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
        assertThatThrownBy(() -> authorize(request("GET","/internal/api/bot/channels/{channelId}",Map.of("channelId","22")),"{}"))
            .isInstanceOf(ResponseStatusException.class);
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"POST,/reopen","PUT,/activity","DELETE,/activity","POST,/operator-relay","PUT,/operator-messages/{telegramMessageId}"})
    void childCannotClaimPanelOperatorAuthorityEvenForItsOwnTicket(String method,String suffix) {
        var request=request(method,"/internal/api/bot/tickets/{ticketId}"+suffix,Map.of("ticketId","own-root"));
        assertThatThrownBy(()->authorize(request,"{\"operatorIdentity\":\"admin\"}"))
            .isInstanceOf(ResponseStatusException.class);
        verify(jdbc,never()).queryForList(eq("SELECT channel_id FROM tickets WHERE ticket_id=? FOR UPDATE"),any(Object[].class));
    }
    @ParameterizedTest @ValueSource(strings={"","/request-number"})
    void childCanStillReadItsOwnTicket(String suffix) {
        var request=request("GET","/internal/api/bot/tickets/{ticketId}"+suffix,Map.of("ticketId","own-root"));
        authorize(request,"{}");assertThat(ChildBotRequestScopeService.channelId(request)).isEqualTo(22L);
    }

}
