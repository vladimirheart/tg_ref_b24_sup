package com.example.panel.controller;

import com.example.panel.security.SecurityConfig;
import com.example.panel.service.ChannelTransportService;
import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.service.BotLifecycleCommandResult;
import com.example.panel.service.BotProcessService;
import com.example.panel.service.BotRunnerLifecycleClient;
import com.example.panel.service.BotRuntimeContractService;
import com.example.panel.service.UiEventStreamService;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
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
    ChannelTelegramDiagnosticsApiController.class, ChannelNotificationApiController.class, BotProcessApiController.class})
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
        new Route("GET", "/api/channel-notifications", "notifications"),
        new Route("POST", "/api/bots/" + ID + "/start", "bot-start"),
        new Route("POST", "/api/bots/" + ID + "/stop", "bot-stop"),
        new Route("GET", "/api/bots/" + ID + "/status", "bot-status"),
        new Route("GET", "/api/bots/" + ID + "/runtime-contract", "bot-contract"));

    @Autowired private MockMvc mvc;
    @MockBean private ChannelTransportService transport;
    @MockBean private BotProcessService bots;
    @MockBean private BotRunnerLifecycleClient runner;
    @MockBean private ChannelRepository channels;
    @MockBean private UiEventStreamService events;
    @MockBean private RuntimeRoleProperties runtime;
    private Channel channel;

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
        channel = new Channel(); channel.setId(ID);
        when(channels.findById(ID)).thenReturn(Optional.of(channel));
        when(runtime.getRole()).thenReturn("web");
        when(runner.start(ID)).thenReturn(new BotLifecycleCommandResult("cmd-start",ID,"start",true,"running","2026-10-27T00:00:00Z","fixture-runner"));
        when(runner.stop(ID)).thenReturn(new BotLifecycleCommandResult("cmd-stop",ID,"stop",true,"stopped",null,"fixture-runner"));
        when(bots.status(ID)).thenReturn(new BotProcessService.BotProcessStatus(true,"running",OffsetDateTime.parse("2026-10-27T00:00:00Z")));
        when(bots.describeRuntimeContract(channel)).thenReturn(new BotRuntimeContractService.BotRuntimeContract(ID,"telegram","bot-telegram","auto","jar","jar","fixture","fixture.jar",List.of(),List.of(),List.of(),null,null,null));
    }
    private static ResponseEntity<Map<String, Object>> ok(String marker) { return ResponseEntity.ok(Map.of("success", true, "marker", marker)); }
    private MockHttpServletRequestBuilder build(Route route) {
        return request(HttpMethod.valueOf(route.method()), route.path()).contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON).content(JSON);
    }
    private record Actor(String username, String... authorities) {}
    static Stream<Arguments> denied() {
        return ROUTES.stream().flatMap(route -> Stream.of(
            new Actor("operator","PAGE_CHANNELS"), new Actor("operator","PAGE_SETTINGS"), new Actor("operator","ROLE_ADMIN"),
            new Actor("operator","ROLE_PORTAL_ADMIN","PAGE_SETTINGS"), new Actor("operator","BUSINESS_ADMIN","PAGE_CHANNELS"),
            new Actor("operator","ALL_BUSINESSES","PAGE_SETTINGS"), new Actor("operator","ROLE_ADMIN","PAGE_DIALOGS"),
            new Actor("admin","PAGE_CHANNELS","PAGE_SETTINGS"), new Actor(" admin ","PAGE_SETTINGS"))
            .map(actor -> Arguments.of(route, actor)));
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
    void pageRoleBusinessGrantOrAdminNameAloneDoesNotAuthorizeManagement(Route route, Actor actor) throws Exception {
        mvc.perform(build(route).with(user(actor.username()).authorities(Arrays.stream(actor.authorities()).map(SimpleGrantedAuthority::new).toList())).with(csrf()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(transport,bots,runner,channels,events,runtime);
    }

    @ParameterizedTest @MethodSource("allowed")
    void systemAdminWithEitherManagementPageAuthorityRetainsDelegation(Route route, String authority) throws Exception {
        var req = build(route).with(user("manager").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),new SimpleGrantedAuthority(authority)));
        if (route.writes()) req.with(csrf());
        var response = mvc.perform(req).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        if (!route.operation().startsWith("bot-")) response.andExpect(jsonPath("$.marker").value(route.operation()));
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
            case "bot-start" -> {
                verify(channels).findById(ID);verify(runtime).getRole();verify(runner).start(ID);verify(events).publishSidebarBotsChanged("bot_started",ID);
                response.andExpect(jsonPath("$.commandId").value("cmd-start")).andExpect(jsonPath("$.status").value("running"));
            }
            case "bot-stop" -> {
                verify(runtime).getRole();verify(runner).stop(ID);verify(events).publishSidebarBotsChanged("bot_stopped",ID);
                response.andExpect(jsonPath("$.commandId").value("cmd-stop")).andExpect(jsonPath("$.status").value("stopped"));
            }
            case "bot-status" -> {verify(bots).status(ID);response.andExpect(jsonPath("$.status").value("running"));}
            case "bot-contract" -> {verify(channels).findById(ID);verify(bots).describeRuntimeContract(channel);response.andExpect(jsonPath("$.contract.botModule").value("bot-telegram"));}
            default -> throw new AssertionError(route);
        }
        verifyNoMoreInteractions(transport,bots,runner,channels,events,runtime);
    }

    @ParameterizedTest @MethodSource("csrfFailures")
    void pageAuthorityDoesNotBypassMissingOrInvalidCsrf(Route route, String authority, boolean invalid) throws Exception {
        var req = build(route).with(user("manager").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),new SimpleGrantedAuthority(authority)));
        if (invalid) req.with(csrf().useInvalidToken());
        mvc.perform(req).andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(transport,bots,runner,channels,events,runtime);
    }

    @ParameterizedTest @MethodSource("anonymous")
    void anonymousRequestCannotReachTransportEvenWithValidCsrf(Route route) throws Exception {
        mvc.perform(build(route).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(transport,bots,runner,channels,events,runtime);
    }
}
