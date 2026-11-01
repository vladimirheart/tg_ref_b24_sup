package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.supportbot.entity.Channel;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingPlatformIsolationTest {
    private static final long USER=9_007_199_254_740_993L;
    static Stream<Arguments> unavailable(){return Stream.of("missing-channel","missing-id","zero-id","negative-id","inactive","null-platform","blank-platform","unknown-platform","missing-vk","missing-max").flatMap(change->Stream.of(false,true).map(support->Arguments.of(change,support)));}
    static Stream<Arguments> destinations(){return Stream.of("telegram","vk","max").flatMap(platform->Stream.of(false,true).map(support->Arguments.of(platform,support)));}
    static Stream<Arguments> exact(){return destinations().flatMap(a->Stream.of(false,true).map(uppercase->Arguments.of(a.get()[0],a.get()[1],uppercase)));}
    static Stream<String> platforms(){return Stream.of("telegram","vk","max");}

    @ParameterizedTest @MethodSource("unavailable")
    void unresolvedOrUnavailableDestinationNeverUsesTelegram(String change,boolean support){
        var telegram=messenger("telegram",true);var service=new MessagingService(List.of(telegram));
        Channel channel=channel("telegram");
        switch(change){
            case "missing-channel" -> channel=null;
            case "missing-id" -> channel.setId(null);
            case "zero-id" -> channel.setId(0L);
            case "negative-id" -> channel.setId(-1L);
            case "inactive" -> channel.setActive(false);
            case "null-platform" -> channel.setPlatform(null);
            case "blank-platform" -> channel.setPlatform(" ");
            case "unknown-platform" -> channel.setPlatform("foreign");
            case "missing-vk" -> channel.setPlatform("vk");
            case "missing-max" -> channel.setPlatform("max");
            default -> throw new AssertionError(change);
        }
        assertThat(send(service,channel,support)).isFalse();assertNoSend(telegram);
    }
    @ParameterizedTest @MethodSource("exact")
    void resolvedDestinationUsesOnlyExactRegisteredProvider(String platform,boolean support,boolean uppercase){
        var chosen=messenger(platform,true);var other=messenger("unrelated",true);
        var service=new MessagingService(List.of(other,chosen));
        assertThat(send(service,channel(uppercase?platform.toUpperCase(Locale.ROOT):platform),support)).isTrue();
        if(support)verify(chosen).sendToSupportChat("-100","payload");else verify(chosen).sendToUser(USER,"payload");
        assertNoSend(other);
    }
    @ParameterizedTest @MethodSource("platforms")
    void duplicatePlatformProvidersAreRejectedInsteadOfChoosingCredentialsByListOrder(String platform){
        assertThatThrownBy(()->new MessagingService(List.of(messenger(platform,true),messenger(platform.toUpperCase(Locale.ROOT),true))))
            .isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @MethodSource("destinations")
    void failedProviderNeverTriggersAnotherPlatform(String platform,boolean support){
        var chosen=messenger(platform,false);var other=messenger("unrelated",true);
        assertThat(send(new MessagingService(List.of(chosen,other)),channel(platform),support)).isFalse();assertNoSend(other);
    }
    @ParameterizedTest @MethodSource("destinations")
    void providerExceptionNeverTriggersAnotherPlatform(String platform,boolean support){
        var chosen=messenger(platform,true);var other=messenger("unrelated",true);
        when(chosen.sendToUser(anyLong(),anyString())).thenThrow(new IllegalStateException("isolated failure"));
        when(chosen.sendToSupportChat(anyString(),anyString())).thenThrow(new IllegalStateException("isolated failure"));
        assertThatThrownBy(()->send(new MessagingService(List.of(chosen,other)),channel(platform),support)).isInstanceOf(IllegalStateException.class);
        assertNoSend(other);
    }
    private boolean send(MessagingService service,Channel channel,boolean support){return support?service.sendToSupportChat(channel,"payload"):service.sendToUser(channel,USER,"payload");}
    static Channel channel(String platform){var channel=new Channel();channel.setId(USER+2);channel.setPlatform(platform);channel.setSupportChatId("-100");return channel;}
    static OutboundMessenger messenger(String platform,boolean success){var messenger=mock(OutboundMessenger.class);when(messenger.platform()).thenReturn(platform);when(messenger.sendToUser(anyLong(),anyString())).thenReturn(success);when(messenger.sendToSupportChat(anyString(),anyString())).thenReturn(success);return messenger;}
    static void assertNoSend(OutboundMessenger messenger){verify(messenger,never()).sendToUser(anyLong(),anyString());verify(messenger,never()).sendToSupportChat(anyString(),anyString());}
}
