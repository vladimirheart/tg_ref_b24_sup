package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class MessagingObservedReceiptTest {
    static Stream<Arguments> cases(){return Stream.of("telegram","vk","max").flatMap(p->Stream.of("ok","deny","handoff-error","channel-change","token-change","null-result","unknown","journal-error","legacy-success","rotate-after-send").map(c->Arguments.of(p,c)));}
    @ParameterizedTest @MethodSource("cases")
    void pinsSenderAndObservesExactlyOneProviderOutcome(String platform,String change){
        var channel=MessagingPlatformIsolationTest.channel(platform);
        var provider=MessagingPlatformIsolationTest.messenger(platform,true);
        var identity=provider.senderIdentity();var observer=mock(OutboundDeliveryObserver.class);
        var api=mock(PanelOutboundSenderAuthorizationClient.class);when(api.authorize(identity)).thenReturn(!change.equals("deny"));
        when(provider.sendToUserWithReceipt(77L,"prompt")).thenAnswer(call->{
            if(change.equals("rotate-after-send"))when(provider.senderIdentity()).thenReturn(new OutboundSenderIdentity(identity.channelId(),platform,"b".repeat(64)));
            return switch(change){case "null-result"->null;case "unknown"->ProviderSendResult.unknown();case "legacy-success"->ProviderSendResult.legacy(true);default->ProviderSendResult.accepted("mid.42");};
        });
        doAnswer(call->{
            if(change.equals("handoff-error"))throw new IllegalStateException("Test: prepare unavailable");
            if(change.equals("channel-change"))channel.setActive(false);
            if(change.equals("token-change"))when(provider.senderIdentity()).thenReturn(new OutboundSenderIdentity(identity.channelId(),platform,"b".repeat(64)));
            return null;
        }).when(observer).beforeProvider(identity);
        if(change.equals("journal-error"))doThrow(new IllegalStateException("Test: journal unavailable")).when(observer).accepted(any(),anyLong(),any());
        var service=new MessagingService(List.of(provider),api);
        if(List.of("handoff-error","journal-error").contains(change))assertThatThrownBy(()->service.sendToUserObserved(channel,77L,"prompt",observer)).isInstanceOf(IllegalStateException.class);
        else assertThat(service.sendToUserObserved(channel,77L,"prompt",observer)).isEqualTo(List.of("ok","legacy-success","rotate-after-send").contains(change));
        boolean invoked=!List.of("deny","handoff-error","channel-change","token-change").contains(change);
        verify(provider,times(invoked?1:0)).sendToUserWithReceipt(77L,"prompt");
        verify(provider,never()).sendToUser(anyLong(),anyString());verify(provider,never()).sendToSupportChat(anyString(),anyString());
        if(List.of("ok","journal-error","rotate-after-send").contains(change))verify(observer).accepted(identity,77L,ProviderSendResult.accepted("mid.42"));
        else verify(observer,never()).accepted(any(),anyLong(),any());
        assertThat(ProviderSendResult.accepted("mid.42").toString()).doesNotContain("mid.42");
    }
}
