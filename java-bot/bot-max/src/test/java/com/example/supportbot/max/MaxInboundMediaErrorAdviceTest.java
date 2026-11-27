package com.example.supportbot.max;

import com.example.supportbot.service.InboundMediaException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MaxInboundMediaErrorAdviceTest {
    @Test void incomingFailureReturns503SafeJsonWithoutAcknowledgingProcessing() {
        var response=new MaxInboundMediaErrorAdvice().media(new InboundMediaException("max",new java.io.IOException("HTTP 413 token=private")));
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).containsEntry("success",false).containsEntry("ok",false).containsEntry("errorSource","max");
        assertThat(response.getBody().get("error").toString()).doesNotContain("private");
    }
}
