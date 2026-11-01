package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.supportbot.entity.Channel;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingSenderIdentityTest {
    private static final long CHANNEL = 9_007_199_254_740_993L;
    static Stream<Arguments> destinations() {
        return Stream.of("telegram", "vk", "max").flatMap(platform -> Stream.of(false, true)
            .map(support -> Arguments.of(platform, support)));
    }
    static Stream<Arguments> invalid() {
        return destinations().flatMap(destination -> Stream.of("missing-proof", "foreign-channel", "foreign-platform",
            "null-hash", "blank-hash", "invalid-hash").map(change -> Arguments.of(destination.get()[0], destination.get()[1], change)));
    }

    @ParameterizedTest @MethodSource("invalid")
    void foreignOrMissingProviderProofNeverSends(String platform, boolean support, String change) throws Exception {
        var provider = provider(platform, change, new AtomicBoolean());
        assertThat(send(service(provider, null), channel(platform), support)).isFalse();
        verifyNoProviderSend(provider);
    }
    @ParameterizedTest @MethodSource("destinations")
    void exactProviderProofStillDelivers(String platform, boolean support) throws Exception {
        var provider = provider(platform, "valid", new AtomicBoolean());
        assertThat(send(service(provider, null), channel(platform), support)).isTrue();
        if (support) verify(provider).sendToSupportChat("-100", "payload");
        else verify(provider).sendToUser(77L, "payload");
    }
    @ParameterizedTest @MethodSource("destinations")
    void credentialChangedWhileAwaitingAuthorizationNeverSends(String platform, boolean support) throws Exception {
        var changed = new AtomicBoolean();
        var provider = provider(platform, "valid", changed);
        assertThat(send(service(provider, () -> changed.set(true)), channel(platform), support)).isFalse();
        verifyNoProviderSend(provider);
    }

    private OutboundMessenger provider(String platform, String change, AtomicBoolean changed) {
        return mock(OutboundMessenger.class, call -> switch (call.getMethod().getName()) {
            case "platform" -> platform;
            case "senderIdentity" -> proof(platform, change, changed.get());
            case "sendToUser", "sendToSupportChat" -> true;
            default -> RETURNS_DEFAULTS.answer(call);
        });
    }
    private Object proof(String platform, String change, boolean changed) throws Exception {
        if (change.equals("missing-proof")) return null;
        Long channel = change.equals("foreign-channel") ? CHANNEL + 1 : CHANNEL;
        String identityPlatform = change.equals("foreign-platform") ? "foreign" : platform;
        String hash = switch (change) { case "null-hash" -> null; case "blank-hash" -> " "; case "invalid-hash" -> "invalid"; default -> (changed ? "b" : "a").repeat(64); };
        return Class.forName("com.example.supportbot.service.OutboundSenderIdentity")
            .getConstructor(Long.class, String.class, String.class).newInstance(channel, identityPlatform, hash);
    }
    // Setup-only bridge for baseline one-dependency and fixed two-dependency constructors.
    private MessagingService service(OutboundMessenger provider, Runnable authorizationEffect) throws Exception {
        Constructor<?>[] constructors = MessagingService.class.getConstructors();
        if (constructors.length != 1) throw new AssertionError("Unexpected service constructors");
        var constructor = constructors[0];
        if (constructor.getParameterCount() == 1) return (MessagingService) constructor.newInstance(List.of(provider));
        Object admission = mock(constructor.getParameterTypes()[1], call -> {
            if (call.getMethod().getName().equals("authorize")) {
                if (authorizationEffect != null) authorizationEffect.run();
                return true;
            }
            return RETURNS_DEFAULTS.answer(call);
        });
        return (MessagingService) constructor.newInstance(List.of(provider), admission);
    }
    private boolean send(MessagingService service, Channel channel, boolean support) {
        return support ? service.sendToSupportChat(channel, "payload") : service.sendToUser(channel, 77L, "payload");
    }
    private Channel channel(String platform) { var channel = new Channel(); channel.setId(CHANNEL); channel.setPlatform(platform); channel.setSupportChatId("-100"); return channel; }
    private void verifyNoProviderSend(OutboundMessenger provider) {
        verify(provider, never()).sendToUser(anyLong(), anyString());
        verify(provider, never()).sendToSupportChat(anyString(), anyString());
    }
}
