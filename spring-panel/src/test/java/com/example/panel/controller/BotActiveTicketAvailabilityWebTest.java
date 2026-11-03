package com.example.panel.controller;

import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The completion marker is emitted only after authorization and a successful read. */
class BotActiveTicketAvailabilityWebTest {
    static final String HEADER="X-Iguana-Active-Lookup-Version";
    final BotRuntimeTicketReadService tickets=mock(BotRuntimeTicketReadService.class);
    final InternalBotApiRequestGuardService guard=mock(InternalBotApiRequestGuardService.class);
    MockMvc mvc;
    @BeforeEach void setup(){
        doAnswer(call->{if(!"fixture-token".equals(call.getArgument(1)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return null;}).when(guard).authorize(any(),any());
        mvc=MockMvcBuilders.standaloneSetup(new BotRuntimeReadApiController(tickets,mock(BotRuntimeChannelService.class),mock(BotRuntimeConfigService.class),mock(BotRuntimeBlacklistService.class),guard)).build();
    }
    static Stream<Arguments> lookups(){return Stream.of(1L,1001L,9007199254740993L,Long.MAX_VALUE).flatMap(user->Stream.of(false,true).map(found->Arguments.of(user,found)));}
    @ParameterizedTest @MethodSource("lookups") void completedReadMarksFoundAndAbsentResults(long user,boolean found)throws Exception{
        when(tickets.findActiveTicket(user," @client ",55L)).thenReturn(found?Optional.of(new BotRuntimeTicketReadService.ActiveTicketLookup(" raw-ticket "," @client ",OffsetDateTime.parse("2026-11-03T07:00:00Z"))):Optional.empty());
        var result=mvc.perform(get("/internal/api/bot/tickets/active").header("X-Iguana-Bot-Api-Token","fixture-token").param("userId",Long.toString(user)).param("username"," @client ").param("channelId","55"))
            .andExpect(status().is(found?200:404)).andExpect(header().string(HEADER,"1")).andExpect(header().string("Cache-Control","no-store")).andReturn();
        if(found)assertThat(result.getResponse().getContentAsString()).contains("\"ticketId\":\" raw-ticket \"");
        var order=inOrder(guard,tickets);order.verify(guard).authorize(any(),eq("fixture-token"));order.verify(tickets).findActiveTicket(user," @client ",55L);
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"wrong"}) void unauthorizedRequestNeverReceivesCompletionProof(String token)throws Exception{
        var request=get("/internal/api/bot/tickets/active").param("userId","1001").param("channelId","55");if(token!=null)request.header("X-Iguana-Bot-Api-Token",token);
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(HEADER));verifyNoInteractions(tickets);
    }
    @ParameterizedTest @ValueSource(ints={400,401,403,404,409,500,503}) void readFailureNeverReceivesAnAbsenceProof(int code)throws Exception{
        when(tickets.findActiveTicket(1001L,null,55L)).thenThrow(new ResponseStatusException(HttpStatus.valueOf(code),"read unavailable"));
        mvc.perform(get("/internal/api/bot/tickets/active").header("X-Iguana-Bot-Api-Token","fixture-token").param("userId","1001").param("channelId","55"))
            .andExpect(status().is(code)).andExpect(header().doesNotExist(HEADER));
    }
    @ParameterizedTest @ValueSource(strings={"userId=not-a-number","channelId=9223372036854775808","unknown-route"}) void bindingAndRoutingFailuresNeverReceiveCompletionProof(String mode)throws Exception{
        var request=get(mode.equals("unknown-route")?"/internal/api/bot/tickets/active/unknown":"/internal/api/bot/tickets/active").header("X-Iguana-Bot-Api-Token","fixture-token");
        if(!mode.equals("unknown-route")){var pair=mode.split("=");request.param(pair[0],pair[1]);}
        mvc.perform(request).andExpect(status().is(mode.equals("unknown-route")?404:400)).andExpect(header().doesNotExist(HEADER));verifyNoInteractions(tickets);
    }
}
