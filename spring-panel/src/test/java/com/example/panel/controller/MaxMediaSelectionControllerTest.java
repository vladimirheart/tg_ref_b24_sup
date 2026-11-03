package com.example.panel.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.SharedGeneralQuestionnaireService;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.web.server.ResponseStatusException;

class MaxMediaSelectionControllerTest {
    @ParameterizedTest @ValueSource(strings={"BUSINESS","GENERAL","disabled","denied","missing","read-only"})
    void capabilityIsOnlyEmittedAfterAuthorizedWriterSelection(String kind) {
        var service=mock(SharedGeneralQuestionnaireService.class);var guard=mock(InternalBotApiRequestGuardService.class);
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(!kind.equals("disabled"));flags.setSharedChannelResolution(true);flags.setGeneralChannelResolution(true);flags.setTicketWriteRequiresBusiness(true);
        String scope=kind.equals("GENERAL")?"GENERAL":"BUSINESS";Long business=scope.equals("GENERAL")?null:7L;
        var selected=new SharedGeneralQuestionnaireService.Selected(4,55,scope,"USER_SELECTION",business,"q","a".repeat(64),"c".repeat(64),Map.of(),Map.of(),Map.of());
        when(service.selectExpanded(55,scope,business,"c".repeat(64))).thenReturn(kind.equals("missing")?Optional.empty():Optional.of(selected));
        var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
        if(kind.equals("denied"))doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(guard).authorize(request,"fixture");
        var controller=new SharedExpandedQuestionnaireRuntimeController(service,guard,flags);
        if(!kind.equals("BUSINESS")&&!kind.equals("GENERAL")) {
            assertThatThrownBy(()->controller.maxMediaSelection(request,response,"fixture",55,scope,business,"c".repeat(64),!kind.equals("read-only"))).isInstanceOf(ResponseStatusException.class);
            assertThat(response.getHeader("X-Iguana-Questionnaire-MAX-Media-Version")).isNull();
        }else{
            assertThat(controller.maxMediaSelection(request,response,"fixture",55,scope,business,"c".repeat(64),true)).isEqualTo(selected);
            assertThat(response.getHeader("X-Iguana-Questionnaire-MAX-Media-Version")).isEqualTo("1");assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        }
        verify(guard).authorize(request,"fixture");
    }
}
