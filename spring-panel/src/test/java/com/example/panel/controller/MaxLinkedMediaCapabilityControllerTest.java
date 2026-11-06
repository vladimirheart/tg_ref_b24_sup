package com.example.panel.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.SharedGeneralQuestionnaireService;
import com.example.panel.service.integration.SelectedTicketFollowupMediaService;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.web.server.ResponseStatusException;

class MaxLinkedMediaCapabilityControllerTest {
    @ParameterizedTest @ValueSource(strings={"BUSINESS","GENERAL","revoked","missing-schema","missing-component"})
    void initialCapabilityRequiresAuthorizedSelectionAndTheLinkedSchema(String kind) {
        var selection=mock(SharedGeneralQuestionnaireService.class);var guard=mock(InternalBotApiRequestGuardService.class);var media=mock(SelectedTicketFollowupMediaService.class);
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setSharedChannelResolution(true);flags.setGeneralChannelResolution(true);flags.setTicketWriteRequiresBusiness(true);
        var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();String scope=kind.equals("GENERAL")?"GENERAL":"BUSINESS";Long business=kind.equals("GENERAL")?null:7L;
        var chosen=new SharedGeneralQuestionnaireService.Selected(4,55,scope,"USER_SELECTION",business,"q","a".repeat(64),"c".repeat(64),Map.of(),Map.of(),Map.of());
        when(selection.selectExpanded(55,scope,business,"c".repeat(64))).thenReturn(Optional.of(chosen));
        var controller=new SharedExpandedQuestionnaireRuntimeController(selection,guard,flags);if(!kind.equals("missing-component"))controller.setLinkedMedia(media);
        if(kind.equals("revoked"))doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(guard).authorize(request,"child");
        if(kind.equals("missing-schema"))doThrow(new IllegalStateException("V87 missing")).when(media).requireMaxLinkedMedia(55);
        org.assertj.core.api.ThrowableAssert.ThrowingCallable invoke=()->controller.maxLinkedMediaSelection(request,response,"child",55,scope,business,"c".repeat(64),true);
        if(kind.equals("BUSINESS")||kind.equals("GENERAL")){assertThatCode(invoke).doesNotThrowAnyException();verify(media).requireMaxLinkedMedia(55);assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");}
        else assertThatThrownBy(invoke).isInstanceOf(RuntimeException.class);
        if(kind.equals("revoked"))verifyNoInteractions(media,selection);
        verify(guard).authorize(request,"child");
    }
}
