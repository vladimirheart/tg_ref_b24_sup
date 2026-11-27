package com.example.supportbot.vk;

import com.example.supportbot.service.InboundMediaException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class VkInboundMediaErrorAdviceTest {
    @Test void incomingFailureReturns503SafeJsonWithoutAcknowledgingProcessing() {
        var response=new VkInboundMediaErrorAdvice().media(new InboundMediaException("vk",new java.io.IOException("HTTP 403 https://private.example")));
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).containsEntry("success",false).containsEntry("ok",false).containsEntry("errorSource","vk");
        assertThat(response.getBody().get("error").toString()).doesNotContain("private");
    }
}
