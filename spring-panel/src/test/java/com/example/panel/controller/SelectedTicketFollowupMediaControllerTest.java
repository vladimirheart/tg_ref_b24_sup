package com.example.panel.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.panel.config.RuntimeCoordinationProperties;
import com.example.panel.security.*;
import com.example.panel.service.integration.SelectedTicketFollowupMediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SelectedTicketFollowupMediaControllerTest {
    private final SelectedTicketFollowupMediaService service=mock(SelectedTicketFollowupMediaService.class);
    private MockMvc mvc;
    private final String path="/internal/api/bot/channels/8/questionnaire-media?userId=901&ticketId=T-TG";
    @BeforeEach void setup() {
        var properties=new InternalBotApiProperties();properties.setToken("test-token");properties.setSignatureSecret("test-secret");properties.setRequireRequestSignature(true);
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        var guard=new InternalBotApiRequestGuardService(properties,new RuntimeCoordinationProperties(),new ObjectMapper(),redis);
        mvc=MockMvcBuilders.standaloneSetup(new SelectedTicketFollowupMediaController(service,guard)).build();
        when(service.admission(8,901,"T-TG")).thenReturn(new SelectedTicketFollowupMediaService.Admission(1,8,901,"T-TG",null,"GENERAL","q-general","a".repeat(64),"c".repeat(64),9));
    }
    @Test void internalTokenAndSignedTupleAreRequiredBeforeAdmission() throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(path).header("X-Iguana-Bot-Api-Token","test-token")).andExpect(status().isUnauthorized());
        mvc.perform(signed(path).queryParam("userId","902")).andExpect(status().isUnauthorized());
        verify(service,never()).admission(anyLong(),anyLong(),anyString());
    }
    @Test void frozenGeneralAdmissionHasNoBusinessAndIsNotCacheable() throws Exception {
        mvc.perform(signed(path)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.businessId").isEmpty())
            .andExpect(jsonPath("$.scopeKind").value("GENERAL")).andExpect(jsonPath("$.remainingFiles").value(9));
    }
    @Test void unavailableRootOrFlagsNeverReturnAPartialCapability() throws Exception {
        when(service.admission(8,901,"T-TG")).thenThrow(new IllegalStateException("Historical, closed, foreign or disabled"));
        mvc.perform(signed(path)).andExpect(status().isConflict()).andExpect(header().string("Cache-Control","no-store"));
    }
    private MockHttpServletRequestBuilder signed(String target) throws Exception {
        String time=Instant.now().toString();String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=HexFormat.of().formatHex(mac.doFinal(("GET\n"+target+"\n"+time+"\n"+sha).getBytes(StandardCharsets.UTF_8)));
        return get(target).header("X-Iguana-Bot-Api-Token","test-token").header("X-Iguana-Request-Timestamp",time).header("X-Iguana-Request-Signature",signature);
    }
    @Test void vkBatchProofHasADistinctSignedPathAndProtocol() throws Exception {
        String target=path.replace("questionnaire-media?","questionnaire-media/vk-batch?");
        when(service.vkBatchAdmission(8,901,"T-TG")).thenReturn(new SelectedTicketFollowupMediaService.Admission(2,8,901,"T-TG",null,"GENERAL","q-general","a".repeat(64),"c".repeat(64),7));
        mvc.perform(get(target)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(signed(target).queryParam("ticketId","FOREIGN")).andExpect(status().isUnauthorized());
        verify(service,never()).vkBatchAdmission(anyLong(),anyLong(),anyString());
        mvc.perform(signed(target)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.remainingFiles").value(7)).andExpect(jsonPath("$.businessId").isEmpty()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void deniedVkBatchCannotReuseTheTelegramProof() throws Exception {
        when(service.vkBatchAdmission(8,901,"T-TG")).thenThrow(new IllegalStateException("Unavailable"));
        mvc.perform(signed(path.replace("questionnaire-media?","questionnaire-media/vk-batch?"))).andExpect(status().isConflict());
        verify(service,never()).admission(anyLong(),anyLong(),anyString());
    }
}
