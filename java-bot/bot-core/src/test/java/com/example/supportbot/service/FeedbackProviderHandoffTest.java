package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Real dispatcher/router: durable boundary precedes the SDK and ambiguous results never become retryable. */
class FeedbackProviderHandoffTest {
    static Stream<Arguments> cases() {
        return Stream.of("telegram","vk","max").flatMap(p->Stream.of("ok","deny","authorization-error","ledger-error",
            "provider-false","provider-error","result-write-error","late-channel-change").map(c->Arguments.of(p,c)));
    }
    @ParameterizedTest @MethodSource("cases")
    void preservesProviderBoundaryAndFailureProvenance(String platform,String change) {
        var channel=MessagingPlatformIsolationTest.channel(platform);long user=9007199254740993L;
        var channels=mock(ChannelService.class);when(channels.findById(channel.getId())).thenReturn(Optional.of(channel));
        var ledger=mock(OutboundTransportDeliveryLedgerService.class);
        when(ledger.beginDelivery(anyString(),anyString(),anyString(),anyLong(),anyLong(),anyString(),anyLong())).thenReturn(true);
        var recipient=mock(PanelFeedbackPromptDeliveryClient.class);when(recipient.authorize(any(),anyString())).thenReturn(true);
        var sender=mock(PanelOutboundSenderAuthorizationClient.class);when(sender.authorize(any())).thenReturn(true);
        var provider=MessagingPlatformIsolationTest.messenger(platform,true);
        switch(change){
            case "deny" -> when(sender.authorize(any())).thenReturn(false);
            case "authorization-error" -> when(sender.authorize(any())).thenThrow(new IllegalStateException("API unavailable"));
            case "ledger-error" -> doThrow(new IllegalStateException("handoff commit unavailable")).when(ledger).markSending("r260");
            case "provider-false" -> when(provider.sendToUser(anyLong(),anyString())).thenReturn(false);
            case "provider-error" -> when(provider.sendToUser(anyLong(),anyString())).thenThrow(new IllegalStateException("unknown outcome"));
            case "result-write-error" -> doThrow(new IllegalStateException("result commit unavailable")).when(ledger).markDelivered("r260");
            case "late-channel-change" -> doAnswer(call->{channel.setActive(false);return null;}).when(ledger).markSending("r260");
            default -> { }
        }
        var dispatch=new OutboundFeedbackPromptDispatchService(channels,new MessagingService(List.of(provider),sender),ledger,recipient);
        var event=new OutboundFeedbackPromptEvent("r260","feedback.prompt.dispatch","r260",platform,channel.getId(),100L,user," padded/Ю%?' ","prompt");
        if(change.equals("ok"))dispatch.dispatch(event,"isolated.route");
        else assertThatThrownBy(()->dispatch.dispatch(event,"isolated.route")).isInstanceOf(IllegalStateException.class);
        boolean entered=!List.of("deny","authorization-error","ledger-error").contains(change);
        if(!entered){
            MessagingPlatformIsolationTest.assertNoSend(provider);verify(ledger).markFailed(eq("r260"),any());verify(ledger,never()).markUncertain(anyString(),any());
        }else if(!change.equals("ok")){
            verify(ledger).markUncertain(eq("r260"),any());verify(ledger,never()).markFailed(anyString(),any());
        }
        if(change.equals("late-channel-change"))MessagingPlatformIsolationTest.assertNoSend(provider);
        if(entered&&!change.equals("late-channel-change")){
            var order=inOrder(ledger,provider);order.verify(ledger).markSending("r260");order.verify(provider).sendToUser(user,"prompt");
            if(change.equals("ok"))order.verify(ledger).markDelivered("r260");
        }
    }
}
