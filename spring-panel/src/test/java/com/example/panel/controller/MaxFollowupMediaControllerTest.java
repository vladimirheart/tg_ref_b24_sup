package com.example.panel.controller;

import com.example.panel.config.RuntimeCoordinationProperties;
import com.example.panel.security.*;
import com.example.panel.service.integration.SelectedTicketFollowupMediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaxFollowupMediaControllerTest {
    final SelectedTicketFollowupMediaService service=mock(SelectedTicketFollowupMediaService.class);
    final String target="/internal/api/bot/channels/55/questionnaire-media/max-batch?userId=1001&ticketId=%20raw-max%20";
    MockMvc mvc;
    @BeforeEach @SuppressWarnings("unchecked") void setup(){
        var properties=new InternalBotApiProperties();properties.setToken("fixture-token");properties.setSignatureSecret("fixture-secret");properties.setRequireRequestSignature(true);
        ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        var guard=new InternalBotApiRequestGuardService(properties,new RuntimeCoordinationProperties(),new ObjectMapper(),redis);
        mvc=MockMvcBuilders.standaloneSetup(new SelectedTicketFollowupMediaController(service,guard)).build();
    }
    void result(SelectedTicketFollowupMediaService.Admission proof)throws Exception{
        try{var call=(SelectedTicketFollowupMediaService.Admission)service.getClass().getMethod("maxBatchAdmission",long.class,long.class,String.class).invoke(service,55L,1001L," raw-max ");when(call).thenReturn(proof);}
        catch(NoSuchMethodException baseline){when(service.vkBatchAdmission(55,1001," raw-max ")).thenReturn(proof);}
    }
    MockHttpServletRequestBuilder signed(String path)throws Exception{return signed(path,path);}
    MockHttpServletRequestBuilder signed(String path,String canonicalPath)throws Exception{
        String time=Instant.now().toString(),sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("fixture-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return get(URI.create(path)).header("X-Iguana-Bot-Api-Token","fixture-token").header("X-Iguana-Request-Timestamp",time)
            .header("X-Iguana-Request-Signature",HexFormat.of().formatHex(mac.doFinal(("GET\n"+canonicalPath+"\n"+time+"\n"+sha).getBytes(StandardCharsets.UTF_8))));
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void signedMaxRouteHasDistinctVersionAndExactFrozenIdentity(boolean general)throws Exception{
        result(new SelectedTicketFollowupMediaService.Admission(3,55,1001," raw-max ",general?null:7L,general?"GENERAL":"BUSINESS","frozen","a".repeat(64),"c".repeat(64),8));
        mvc.perform(signed(target)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.version").value(3))
            .andExpect(jsonPath("$.ticketId").value(" raw-max ")).andExpect(jsonPath("$.scopeKind").value(general?"GENERAL":"BUSINESS")).andExpect(jsonPath("$.remainingFiles").value(8));
        verify(service,never()).admission(anyLong(),anyLong(),anyString());verify(service,never()).vkBatchAdmission(anyLong(),anyLong(),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"no-token","no-signature","other-path","other-user","other-ticket"})
    void unsignedOrChangedTupleNeverReachesMaxAdmission(String mode)throws Exception{
        var request=switch(mode){case "no-token"->get(URI.create(target));case "no-signature"->get(URI.create(target)).header("X-Iguana-Bot-Api-Token","fixture-token");case "other-path"->signed(target,target.replace("/max-batch?","/vk-batch?"));case "other-user"->signed(target).queryParam("userId","1002");default->signed(target).queryParam("ticketId","foreign");};
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","no-store"));verifyNoInteractions(service);
    }
    @Test void deniedMaxCapabilityDoesNotFallbackToTelegramOrVk()throws Exception{
        try{var call=(SelectedTicketFollowupMediaService.Admission)service.getClass().getMethod("maxBatchAdmission",long.class,long.class,String.class).invoke(service,55L,1001L," raw-max ");when(call).thenThrow(new IllegalStateException("Unavailable"));}
        catch(NoSuchMethodException baseline){when(service.vkBatchAdmission(55,1001," raw-max ")).thenThrow(new IllegalStateException("Unavailable"));}
        mvc.perform(signed(target)).andExpect(status().isConflict()).andExpect(header().string("Cache-Control","no-store"));verify(service,never()).admission(anyLong(),anyLong(),anyString());verify(service,never()).vkBatchAdmission(anyLong(),anyLong(),anyString());
    }
}
