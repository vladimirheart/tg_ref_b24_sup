package com.example.panel.controller;

import com.example.panel.security.SecurityConfig;
import com.example.panel.service.ChannelTransportService;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ChannelApiController.class, ChannelBotCredentialApiController.class,
    ChannelTelegramDiagnosticsApiController.class, ChannelNotificationApiController.class})
@Import(SecurityConfig.class)
class ChannelManagementAuthorityWebMvcTest {
    private static final long ID = 9007199254740993L;
    private static final Map<String, Object> PAYLOAD = Map.of("channel_name", "Fixture", "business_id", 99);
    private static final String JSON = "{\"channel_name\":\"Fixture\",\"business_id\":99}";
    private record Route(String method, String path, String operation) { boolean writes() { return !method.equals("GET"); } }
    private static final List<Route> ROUTES = List.of(
        new Route("GET", "/api/channels", "list"), new Route("POST", "/api/channels", "create"),
        new Route("PATCH", "/api/channels/" + ID, "update"), new Route("PUT", "/api/channels/" + ID, "update"),
        new Route("POST", "/api/channels/" + ID, "update"), new Route("DELETE", "/api/channels/" + ID, "delete"),
        new Route("POST", "/api/" + ID + "/public-id/regenerate", "regenerate"),
        new Route("POST", "/api/channels/" + ID + "/public-id/regenerate", "regenerate"),
        new Route("GET", "/api/bot-credentials", "credentials"), new Route("POST", "/api/bot-credentials", "create-credential"),
        new Route("DELETE", "/api/bot-credentials/" + ID, "delete-credential"),
        new Route("POST", "/api/channels/" + ID + "/test-message", "test-message"),
        new Route("POST", "/api/channels/" + ID + "/bot-info", "bot-info"),
        new Route("GET", "/api/channel-notifications", "notifications"));

    @Autowired private MockMvc mvc;
    @MockBean private ChannelTransportService transport;

    @BeforeEach void responses() {
        when(transport.getChannels()).thenReturn(ok("list"));
        when(transport.createChannel(any())).thenReturn(ok("create"));
        when(transport.updateChannel(anyLong(), any())).thenReturn(ok("update"));
        when(transport.deleteChannel(anyLong())).thenReturn(ok("delete"));
        when(transport.regeneratePublicId(anyLong())).thenReturn(ok("regenerate"));
        when(transport.getBotCredentials()).thenReturn(ok("credentials"));
        when(transport.createBotCredential(any())).thenReturn(ok("create-credential"));
        when(transport.deleteBotCredential(anyLong())).thenReturn(ok("delete-credential"));
        when(transport.testChannel(anyLong(), any())).thenReturn(ok("test-message"));
        when(transport.refreshBotInfo(anyLong())).thenReturn(ok("bot-info"));
        when(transport.getChannelNotifications()).thenReturn(ok("notifications"));
    }
    private static ResponseEntity<Map<String, Object>> ok(String marker) { return ResponseEntity.ok(Map.of("success", true, "marker", marker)); }
    private MockHttpServletRequestBuilder build(Route route) {
        return request(HttpMethod.valueOf(route.method()), route.path()).contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON).content(JSON);
    }
    static Stream<Arguments> denied() {
        return ROUTES.stream().flatMap(route -> Stream.of("PAGE_DIALOGS", "PAGE_TASKS", "ROLE_ADMIN", "BUSINESS_ADMIN", "ALL_BUSINESSES", "UNRELATED")
            .map(authority -> Arguments.of(route, authority)));
    }
    static Stream<Arguments> allowed() {
        return ROUTES.stream().flatMap(route -> Stream.of("PAGE_CHANNELS", "PAGE_SETTINGS").map(authority -> Arguments.of(route, authority)));
    }
    static Stream<Arguments> csrfFailures() {
        return ROUTES.stream().filter(Route::writes).flatMap(route -> Stream.of("PAGE_CHANNELS", "PAGE_SETTINGS")
            .flatMap(authority -> Stream.of(false, true).map(invalid -> Arguments.of(route, authority, invalid))));
    }
    static Stream<Route> anonymous() { return ROUTES.stream(); }

    @ParameterizedTest @MethodSource("denied")
    void anotherPageRoleOrBusinessGrantDoesNotAuthorizeManagement(Route route, String authority) throws Exception {
        mvc.perform(build(route).with(user("operator").authorities(new SimpleGrantedAuthority(authority))).with(csrf()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(transport);
    }

    @ParameterizedTest @MethodSource("allowed")
    void eitherExistingManagementPageAuthorityRetainsDelegation(Route route, String authority) throws Exception {
        var req = build(route).with(user("manager").authorities(new SimpleGrantedAuthority(authority)));
        if (route.writes()) req.with(csrf());
        mvc.perform(req).andExpect(status().isOk()).andExpect(jsonPath("$.marker").value(route.operation()));
        switch (route.operation()) {
            case "list" -> verify(transport).getChannels();
            case "create" -> verify(transport).createChannel(PAYLOAD);
            case "update" -> verify(transport).updateChannel(ID, PAYLOAD);
            case "delete" -> verify(transport).deleteChannel(ID);
            case "regenerate" -> verify(transport).regeneratePublicId(ID);
            case "credentials" -> verify(transport).getBotCredentials();
            case "create-credential" -> verify(transport).createBotCredential(PAYLOAD);
            case "delete-credential" -> verify(transport).deleteBotCredential(ID);
            case "test-message" -> verify(transport).testChannel(ID, PAYLOAD);
            case "bot-info" -> verify(transport).refreshBotInfo(ID);
            case "notifications" -> verify(transport).getChannelNotifications();
            default -> throw new AssertionError(route);
        }
        verifyNoMoreInteractions(transport);
    }

    @ParameterizedTest @MethodSource("csrfFailures")
    void pageAuthorityDoesNotBypassMissingOrInvalidCsrf(Route route, String authority, boolean invalid) throws Exception {
        var req = build(route).with(user("manager").authorities(new SimpleGrantedAuthority(authority)));
        if (invalid) req.with(csrf().useInvalidToken());
        mvc.perform(req).andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(transport);
    }

    @ParameterizedTest @MethodSource("anonymous")
    void anonymousRequestCannotReachTransportEvenWithValidCsrf(Route route) throws Exception {
        mvc.perform(build(route).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(transport);
    }
}
