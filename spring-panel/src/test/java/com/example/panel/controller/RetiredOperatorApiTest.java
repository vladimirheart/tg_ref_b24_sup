package com.example.panel.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.panel.config.RuntimeCoordinationProperties;
import com.example.panel.security.*;
import com.example.panel.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.ContentCachingRequestWrapper;

/** Real MVC + authentication + local idempotency cache, no DB/service writes. */
class RetiredOperatorApiTest {
    InternalBotApiRequestGuardService guard;
    BotRuntimeTicketWriteService tickets;
    BotRuntimeChannelService channels;
    BotRuntimeBlacklistService blacklist;
    MockMvc mvc;
    static Stream<Arguments> routes() {
        return Stream.of(Arguments.of("POST","/internal/api/bot/tickets/T-22/reopen"),
            Arguments.of("PUT","/internal/api/bot/tickets/T-22/activity"),Arguments.of("DELETE","/internal/api/bot/tickets/T-22/activity"),
            Arguments.of("POST","/internal/api/bot/tickets/T-22/operator-relay"),
            Arguments.of("PUT","/internal/api/bot/tickets/T-22/operator-messages/91"),
            Arguments.of("PUT","/internal/api/bot/channels/22/support-chat"));
    }
    static Stream<Arguments> matrix() {
        return routes().flatMap(a->Stream.of("missing-token","wrong-token","fresh","old-success-cache")
            .map(c->Arguments.of(a.get()[0],a.get()[1],c)));
    }
    @BeforeEach void setup() {
        var properties=new InternalBotApiProperties();properties.setToken("r295-fake-master");properties.setRequireRequestSignature(false);
        var coordination=new RuntimeCoordinationProperties();coordination.setMode("direct");
        @SuppressWarnings("unchecked") var redis=(ObjectProvider<StringRedisTemplate>)mock(ObjectProvider.class);
        guard=spy(new InternalBotApiRequestGuardService(properties,coordination,new ObjectMapper(),redis));
        tickets=mock(BotRuntimeTicketWriteService.class);channels=mock(BotRuntimeChannelService.class);blacklist=mock(BotRuntimeBlacklistService.class);
        mvc=MockMvcBuilders.standaloneSetup(new BotRuntimeWriteApiController(tickets,channels,blacklist,guard)).build();
    }
    @ParameterizedTest @MethodSource("matrix")
    void retiredRouteCannotMutateOrReplayAnEarlierSuccess(String method,String path,String change) throws Exception {
        if(change.equals("old-success-cache")) {
            var raw=new MockHttpServletRequest(method,path);raw.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            raw.addHeader(InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER,"original");
            var wrapped=new ContentCachingRequestWrapper(raw);wrapped.getInputStream().readAllBytes();
            var execution=guard.prepareWrite(wrapped,"r295-fake-master");guard.successResponse(execution,Map.of("updated",true));
            assertThat(guard.prepareWrite(wrapped,"r295-fake-master").replayResponse()).isNotNull();
        }
        clearInvocations(guard);
        var request=MockMvcRequestBuilders.request(HttpMethod.valueOf(method),path).contentType(MediaType.APPLICATION_JSON).content("{}")
            .header(InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER,"original");
        if(!change.equals("missing-token"))request.header(InternalBotApiRequestGuardService.AUTH_HEADER,change.equals("wrong-token")?"wrong":"r295-fake-master");
        mvc.perform(request).andExpect(change.endsWith("token")?status().isUnauthorized():status().isGone());
        verify(guard,never()).prepareWrite(any(),any());
        verifyNoInteractions(tickets,channels,blacklist);
    }
}
