package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.panel.config.RuntimeCoordinationProperties;
import com.example.panel.security.InternalBotApiProperties;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.BotOutboundSenderAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.ContentCachingRequestWrapper;

class BotOutboundSenderAuthorizationControllerTest {
    private static final String PATH="/internal/api/bot/outbound/authorize-sender";
    private static final String BODY="""
        {"channelId":9007199254740993,"platform":"telegram","tokenSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
        """;
    private BotOutboundSenderAuthorizationService authorization;
    private MockMvc mvc;
    @BeforeEach void setup(){
        authorization=mock(BotOutboundSenderAuthorizationService.class);
        var properties=new InternalBotApiProperties();properties.setToken("isolated-r249");properties.setRequireRequestSignature(true);
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        var guard=new InternalBotApiRequestGuardService(properties,new RuntimeCoordinationProperties(),new ObjectMapper(),redis);
        mvc=MockMvcBuilders.standaloneSetup(new BotOutboundSenderAuthorizationController(guard,authorization))
            .addFilter((request,response,chain)->chain.doFilter(new ContentCachingRequestWrapper((HttpServletRequest)request),response)).build();
    }
    @Test void decisionIsNotReplayedFromWriteIdempotencyCache()throws Exception{
        when(authorization.authorize(any())).thenReturn(true,false);
        mvc.perform(signed(BODY).header(InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER,"same-key"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true)).andExpect(jsonPath("$.channelId").value(9007199254740993L))
            .andExpect(jsonPath("$.feedbackTicketContext").doesNotExist()).andExpect(jsonPath("$.prompt").doesNotExist());
        mvc.perform(signed(BODY).header(InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER,"same-key"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        verify(authorization,times(2)).authorize(any());
    }
    @ParameterizedTest @ValueSource(strings={"missing-token","wrong-token","missing-signature","tampered-body"})
    void rejectsInvalidInternalCredentialsBeforeAnyBusinessRead(String change)throws Exception{
        var request=signed(BODY);
        if(change.equals("missing-token") || change.equals("wrong-token"))request.with(mockRequest->{
            mockRequest.removeHeader(InternalBotApiRequestGuardService.AUTH_HEADER);
            if(change.equals("wrong-token"))mockRequest.addHeader(InternalBotApiRequestGuardService.AUTH_HEADER,"foreign-token");
            return mockRequest;
        });
        if(change.equals("missing-signature"))request=post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY).header(InternalBotApiRequestGuardService.AUTH_HEADER,"isolated-r249");
        if(change.equals("tampered-body"))request.content(BODY.replace("telegram","max"));
        mvc.perform(request).andExpect(status().isUnauthorized());verifyNoInteractions(authorization);
    }
    private MockHttpServletRequestBuilder signed(String body)throws Exception{
        String timestamp=OffsetDateTime.now().toString();
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("isolated-r249".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=HexFormat.of().formatHex(mac.doFinal(("POST\n"+PATH+"\n"+timestamp+"\n"+hash).getBytes(StandardCharsets.UTF_8)));
        return post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
            .header(InternalBotApiRequestGuardService.AUTH_HEADER,"isolated-r249")
            .header(InternalBotApiRequestGuardService.TIMESTAMP_HEADER,timestamp)
            .header(InternalBotApiRequestGuardService.SIGNATURE_HEADER,signature);
    }
}
