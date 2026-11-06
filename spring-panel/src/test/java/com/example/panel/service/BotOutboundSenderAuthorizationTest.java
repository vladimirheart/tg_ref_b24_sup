package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.BotOutboundSenderAuthorizationService.SenderIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BotOutboundSenderAuthorizationTest {
    private static final long CHANNEL = 9_007_199_254_740_993L;
    @ParameterizedTest @MethodSource("senders")
    void supportRecipientMustMatchCurrentCanonicalChannel(String platform,boolean bound) throws Exception {
        var f=new Fixture(platform,bound);f.channel.setSupportChatId("-100");
        var identity=new SenderIdentity(CHANNEL,platform,hash(" token/Ю "));
        assertThat(f.service.authorizeSupportChat(identity,"-100")).isTrue();
        assertThat(f.service.authorizeSupportChat(identity,"-101")).isFalse();
        f.channel.setSupportChatId("-102");assertThat(f.service.authorizeSupportChat(identity,"-100")).isFalse();
        f.channel.setActive(false);assertThat(f.service.authorizeSupportChat(identity,"-102")).isFalse();
    }
    static Stream<Arguments> senders() { return Stream.of("telegram", "vk", "max").flatMap(p -> Stream.of(false,true).map(bound -> Arguments.of(p,bound))); }
    static Stream<Arguments> changes() { return senders().flatMap(s -> Stream.of("missing-channel","foreign-channel","inactive-channel","foreign-platform","different-token","unavailable-db","unavailable-config","revoked-binding").map(c -> Arguments.of(s.get()[0],s.get()[1],c))); }

    @ParameterizedTest @MethodSource("senders")
    void authorizesOnlyCurrentExactProviderToken(String platform, boolean bound) throws Exception {
        var fixture = new Fixture(platform,bound);
        assertThat(fixture.service.authorize(new SenderIdentity(CHANNEL,platform,hash(" token/Ю ")))).isTrue();
        assertThat(fixture.service.authorize(new SenderIdentity(CHANNEL,platform,hash("token/Ю")))).isFalse();
    }
    @ParameterizedTest @MethodSource("changes")
    void revokedChangedOrUnavailableSenderIsDenied(String platform, boolean bound, String change) throws Exception {
        var f = new Fixture(platform,bound);
        switch(change) {
            case "missing-channel" -> when(f.channels.findById(CHANNEL)).thenReturn(Optional.empty());
            case "foreign-channel" -> f.channel.setId(CHANNEL+1);
            case "inactive-channel" -> f.channel.setActive(false);
            case "foreign-platform" -> f.channel.setPlatform(platform.equals("vk")?"max":"vk");
            case "different-token" -> {f.channel.setToken("rotated");when(f.config.loadBotCredentials()).thenReturn(List.of(new BotCredential(7L,"selected",platform,"rotated",true)));}
            case "unavailable-db" -> when(f.channels.findById(CHANNEL)).thenThrow(new IllegalStateException("isolated unavailable"));
            case "unavailable-config" -> {f.channel.setCredentialId(7L);when(f.config.loadBotCredentials()).thenThrow(new IllegalStateException("isolated unavailable"));}
            case "revoked-binding" -> {f.channel.setCredentialId(7L);when(f.config.loadBotCredentials()).thenReturn(List.of());}
            default -> throw new AssertionError(change);
        }
        assertThat(f.service.authorize(new SenderIdentity(CHANNEL,platform,hash(" token/Ю ")))).isFalse();
    }
    @ParameterizedTest @MethodSource("senders")
    void doesNotCacheAnAllowAcrossTokenRotation(String platform, boolean bound) throws Exception {
        var f = new Fixture(platform,bound);
        var identity = new SenderIdentity(CHANNEL,platform,hash(" token/Ю "));
        assertThat(f.service.authorize(identity)).isTrue();
        if(bound)when(f.config.loadBotCredentials()).thenReturn(List.of(new BotCredential(7L,"selected",platform,"rotated",true)));
        else f.channel.setToken("rotated");
        assertThat(f.service.authorize(identity)).isFalse();
        assertThat(f.service.authorize(new SenderIdentity(CHANNEL,platform,hash("rotated")))).isTrue();
        verify(f.channels,times(3)).findById(CHANNEL);
    }
    private static class Fixture {
        final ChannelRepository channels=mock(ChannelRepository.class);
        final SharedConfigService config=mock(SharedConfigService.class);
        final Channel channel=new Channel();
        final BotOutboundSenderAuthorizationService service=new BotOutboundSenderAuthorizationService(channels,config);
        Fixture(String platform,boolean bound) {
            channel.setId(CHANNEL);channel.setPlatform(platform);channel.setActive(true);channel.setToken(" token/Ю ");
            if(bound)channel.setCredentialId(7L);
            when(channels.findById(CHANNEL)).thenReturn(Optional.of(channel));
            when(config.loadBotCredentials()).thenReturn(List.of(new BotCredential(7L,"selected",platform," token/Ю ",true)));
        }
    }
    private String hash(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
}
