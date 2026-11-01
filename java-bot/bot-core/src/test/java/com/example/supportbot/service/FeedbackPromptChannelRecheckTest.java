package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.supportbot.entity.Channel;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Actual dispatcher/router together; admission/ledger/providers are isolated doubles. */
class FeedbackPromptChannelRecheckTest {
    private static final long USER=9_007_199_254_740_993L,CHANNEL=USER+2;
    static Stream<String> platforms(){return Stream.of("telegram","vk","max");}
    static Stream<Arguments> changes(){return platforms().flatMap(platform->Stream.of("missing-id","zero-id","foreign-id","other-platform","null-platform","inactive","unavailable-platform").flatMap(change->Stream.of(false,true).map(late->Arguments.of(platform,change,late))));}

    @ParameterizedTest @MethodSource("changes")
    void rejectsMismatchedChannelBeforeOrDuringAuthorization(String platform,String change,boolean late){
        var channel=MessagingPlatformIsolationTest.channel(platform);
        var channels=mock(ChannelService.class);when(channels.findById(CHANNEL)).thenReturn(Optional.of(channel));
        var ledger=mock(OutboundTransportDeliveryLedgerService.class);
        when(ledger.beginDelivery(anyString(),anyString(),anyString(),anyLong(),anyLong(),anyString(),anyLong())).thenReturn(true);
        var authorization=mock(PanelFeedbackPromptDeliveryClient.class);
        var telegram=MessagingPlatformIsolationTest.messenger("telegram",true);
        var vk=MessagingPlatformIsolationTest.messenger("vk",true);var max=MessagingPlatformIsolationTest.messenger("max",true);
        var messaging=new MessagingService(List.of(telegram,vk,max));
        var event=event(platform);String route="isolated."+platform;
        if(late)when(authorization.authorize(event,route)).thenAnswer(call->{change(channel,platform,change);return true;});
        else {change(channel,platform,change);when(authorization.authorize(event,route)).thenReturn(true);}
        var service=new OutboundFeedbackPromptDispatchService(channels,messaging,ledger,authorization);
        assertThatThrownBy(()->service.dispatch(event,route)).isInstanceOf(IllegalStateException.class);
        for(var messenger:List.of(telegram,vk,max))MessagingPlatformIsolationTest.assertNoSend(messenger);
        verify(ledger).markFailed(eq("evt-r250"),any(IllegalStateException.class));verify(ledger,never()).markDelivered(anyString());
        if(late)verify(authorization).authorize(event,route);else verifyNoInteractions(authorization);
    }
    @ParameterizedTest @MethodSource("platforms")
    void unchangedAuthorizedChannelStillDeliversToItsOwnProvider(String platform){
        var channel=MessagingPlatformIsolationTest.channel(platform);var channels=mock(ChannelService.class);
        when(channels.findById(CHANNEL)).thenReturn(Optional.of(channel));
        var ledger=mock(OutboundTransportDeliveryLedgerService.class);
        when(ledger.beginDelivery(anyString(),anyString(),anyString(),anyLong(),anyLong(),anyString(),anyLong())).thenReturn(true);
        var authorization=mock(PanelFeedbackPromptDeliveryClient.class);when(authorization.authorize(any(),anyString())).thenReturn(true);
        var own=MessagingPlatformIsolationTest.messenger(platform,true);var foreign=MessagingPlatformIsolationTest.messenger("foreign",true);
        new OutboundFeedbackPromptDispatchService(channels,new MessagingService(List.of(foreign,own)),ledger,authorization).dispatch(event(platform),"isolated."+platform);
        verify(own).sendToUser(USER,"prompt");MessagingPlatformIsolationTest.assertNoSend(foreign);
        verify(ledger).markDelivered("evt-r250");verify(ledger,never()).markFailed(anyString(),any());
    }
    private void change(Channel channel,String platform,String change){switch(change){
        case "missing-id" -> channel.setId(null);
        case "zero-id" -> channel.setId(0L);
        case "foreign-id" -> channel.setId(CHANNEL+1);
        case "other-platform" -> channel.setPlatform(platform.equals("telegram")?"vk":"telegram");
        case "null-platform" -> channel.setPlatform(null);
        case "inactive" -> channel.setActive(false);
        case "unavailable-platform" -> channel.setPlatform("foreign");
        default -> throw new AssertionError(change);
    }}
    private OutboundFeedbackPromptEvent event(String platform){return new OutboundFeedbackPromptEvent("evt-r250","feedback.prompt.dispatch","evt-r250",platform,CHANNEL,100L,USER," padded/Ю%?' ","prompt");}
}
