package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BotCredentialAdmissionTest {
    static Stream<String> platforms() { return Stream.of("telegram", "vk", "max"); }
    static Stream<Arguments> invalid() {
        return platforms().flatMap(platform -> Stream.of("inactive-channel", "null-channel-id", "zero-channel-id",
            "missing-reference", "duplicate-reference", "inactive-reference", "null-active-reference",
            "foreign-platform", "blank-token", "null-token", "zero-reference", "unknown-channel-platform")
            .map(change -> Arguments.of(platform, change)));
    }

    @ParameterizedTest @MethodSource("invalid")
    void explicitCredentialNeverFallsBackOrIgnoresActivityAndPlatform(String platform, String change) throws Exception {
        var config = mock(SharedConfigService.class);
        var channel = channel(platform);
        channel.setCredentialId(7L);
        var credential = credential(platform, "bound-token", true);
        List<BotCredential> credentials = List.of(credential);
        switch (change) {
            case "inactive-channel" -> channel.setActive(false);
            case "null-channel-id" -> channel.setId(null);
            case "zero-channel-id" -> channel.setId(0L);
            case "missing-reference" -> credentials = List.of();
            case "duplicate-reference" -> credentials = List.of(credential, credential(platform, "other-token", true));
            case "inactive-reference" -> credentials = List.of(credential(platform, "bound-token", false));
            case "null-active-reference" -> credentials = List.of(credential(platform, "bound-token", null));
            case "foreign-platform" -> credentials = List.of(credential(platform.equals("vk") ? "max" : "vk", "bound-token", true));
            case "blank-token" -> credentials = List.of(credential(platform, " ", true));
            case "null-token" -> credentials = List.of(credential(platform, null, true));
            case "zero-reference" -> channel.setCredentialId(0L);
            case "unknown-channel-platform" -> channel.setPlatform("unknown");
            default -> throw new AssertionError(change);
        }
        when(config.loadBotCredentials()).thenReturn(credentials);
        assertThat(resolve(config, channel)).isNull();
    }

    @ParameterizedTest @MethodSource("platforms")
    void unboundChannelUsesItsExactPersistedTokenWithoutReadingTheCredentialCatalog(String platform) throws Exception {
        var config = mock(SharedConfigService.class);
        var result = resolve(config, channel(platform));
        assertThat(result.token()).isEqualTo(" inline-token ");
        assertThat(result.platform()).isEqualTo(platform);
        verifyNoInteractions(config);
    }

    @ParameterizedTest @MethodSource("platforms")
    void validExplicitBindingUsesOnlyTheSelectedCredential(String platform) throws Exception {
        var config = mock(SharedConfigService.class);
        var channel = channel(platform);
        channel.setCredentialId(7L);
        var selected = credential(platform, "bound-token", true);
        when(config.loadBotCredentials()).thenReturn(List.of(new BotCredential(8L,"other",platform,"foreign-token",true), selected));
        assertThat(resolve(config, channel)).isSameAs(selected);
    }

    private BotCredential resolve(SharedConfigService config, Channel channel) throws Exception {
        var process = new BotProcessService(config, new BotProcessProperties(), mock(BotRuntimeContractService.class));
        var method = BotProcessService.class.getDeclaredMethod("resolveCredential", Channel.class);
        method.setAccessible(true);
        return (BotCredential) method.invoke(process, channel);
    }
    private Channel channel(String platform) {
        var channel = new Channel(); channel.setId(9_007_199_254_740_993L); channel.setPlatform(platform);
        channel.setActive(true); channel.setToken(" inline-token "); return channel;
    }
    private BotCredential credential(String platform, String token, Boolean active) {
        return new BotCredential(7L, "selected", platform, token, active);
    }
}
