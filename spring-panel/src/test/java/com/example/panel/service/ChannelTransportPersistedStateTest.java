package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChannelTransportPersistedStateTest {
    private static final long ID = 9_007_199_254_740_993L;
    private static final List<String> OPERATIONS = List.of("test-group", "test-both", "support_chat", "broadcast_channel", "custom_chat");
    private static final List<String> CHANGES = List.of("missing", "query-failure", "null-result", "wrong-id",
        "inactive", "unknown-active", "platform", "inline-token", "credential-id", "support-chat",
        "broadcast-chat", "network-route", "api-root", "null-delivery", "null-platform-config");

    static Stream<Arguments> persistedChanges() {
        return OPERATIONS.stream().flatMap(op -> CHANGES.stream().flatMap(change ->
            Stream.of("before-network", "during-network").map(moment -> Arguments.of(op, change, moment))));
    }

    @ParameterizedTest @MethodSource("persistedChanges")
    void independentDatabaseChangeNeverHandsOffCapturedRequest(String operation, String change, String moment) throws Exception {
        var f = new Fixture(true);
        State captured = State.of(f.channel);
        if (moment.equals("before-network")) f.changePersisted(change);
        else when(f.network.createChannelHttpClient(eq(f.channel), any())).thenAnswer(call -> {
            f.changePersisted(change);
            return f.http;
        });
        assertThat(f.execute(operation)).isFalse();
        assertThat(State.of(f.channel)).isEqualTo(captured); // Different DB object, not a managed-entity mutation.
        verify(f.http, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        if (moment.equals("before-network")) verifyNoInteractions(f.network);
        else verify(f.network).createChannelHttpClient(eq(f.channel), any());
        verify(f.channels, never()).save(any());
    }

    static Stream<Arguments> stableSenders() {
        return OPERATIONS.stream().flatMap(op -> Stream.of(false, true).map(bound -> Arguments.of(op, bound)));
    }

    @ParameterizedTest @MethodSource("stableSenders")
    void unchangedIndependentProjectionAllowsExactTokenAndRecipients(String operation, boolean bound) throws Exception {
        var f = new Fixture(bound);
        assertThat(f.execute(operation)).isTrue();
        int sends = operation.equals("test-both") ? 2 : 1;
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(f.http, times(sends)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requests.getAllValues()).allSatisfy(request ->
            assertThat(request.uri().getPath()).isEqualTo("/bot" + (bound ? "bound-token" : "inline-token") + "/sendMessage"));
        verify(f.channels, never()).save(any());
    }

    @Test void everyRecipientReadsFreshStateBeforeNetworkAndImmediatelyBeforeSend() throws Exception {
        var f = new Fixture(true);
        assertThat(f.execute("test-both")).isTrue();
        verify(f.channels, times(4)).findTransportStateById(ID);
    }

    static Stream<Arguments> localChanges() {
        return OPERATIONS.stream().flatMap(op -> Stream.of("support-chat", "broadcast-chat", "network-route", "api-root")
            .map(change -> Arguments.of(op, change)));
    }

    @ParameterizedTest @MethodSource("localChanges")
    void changingCapturedRoutingObjectDuringNetworkSetupAlsoDenies(String operation, String change) throws Exception {
        var f = new Fixture(true);
        when(f.network.createChannelHttpClient(eq(f.channel), any())).thenAnswer(call -> {
            mutate(f.channel, change);
            return f.http;
        });
        assertThat(f.execute(operation)).isFalse();
        verify(f.http, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @ParameterizedTest @MethodSource("batchChanges")
    void alreadySentRecipientDoesNotAuthorizeNextRecipientAfterDatabaseChange(String change) throws Exception {
        var f = new Fixture(true);
        doAnswer(call -> {
            f.changePersisted(change);
            return f.response;
        }).when(f.http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var body = f.service.testChannel(ID, Map.of("target_mode", "both", "message", "test")).getBody();
        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("sent")).isEqualTo(List.of("-123"));
        assertThat(body.get("failed")).isEqualTo(List.of("-456"));
        verify(f.http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(f.network, times(1)).createChannelHttpClient(eq(f.channel), any());
    }

    static Stream<String> batchChanges() { return CHANGES.stream(); }

    @Test void laterRequestCanUseDeliberatelyUpdatedSettingsButOldRequestIsNeverRerouted() throws Exception {
        var f = new Fixture(true);
        f.changePersisted("support-chat");
        assertThat(f.execute("support_chat")).isFalse();
        verifyNoInteractions(f.network, f.http);
        f.channel.setSupportChatId(f.persisted.getSupportChatId());
        assertThat(f.execute("support_chat")).isTrue();
        var result = f.service.sendConfiguredMessage(ID, "support_chat", null, "test");
        assertThat(result.success()).isTrue();
        assertThat(result.recipient()).isEqualTo("-999");
    }

    static void mutate(Channel c, String change) {
        switch (change) {
            case "wrong-id" -> c.setId(ID + 1);
            case "inactive" -> c.setActive(false);
            case "unknown-active" -> c.setActive(null);
            case "platform" -> c.setPlatform("max");
            case "inline-token" -> c.setToken("different-inline");
            case "credential-id" -> c.setCredentialId(8L);
            case "support-chat" -> c.setSupportChatId("-999");
            case "broadcast-chat" -> c.setDeliverySettings("{\"broadcast_channel_id\":\"-999\"}");
            case "network-route" -> c.setDeliverySettings("{\"broadcast_channel_id\":\"-456\",\"network_route\":{\"route\":\"other\"}}");
            case "api-root" -> c.setPlatformConfig("{\"bot_api_base_url\":\"https://different.invalid\"}");
            case "null-delivery" -> c.setDeliverySettings(null);
            case "null-platform-config" -> c.setPlatformConfig(null);
            default -> throw new AssertionError(change);
        }
    }

    static record State(Long getId, Boolean getActive, String getPlatform, String getToken,
                        Long getCredentialId, String getSupportChatId, String getDeliverySettings,
                        String getPlatformConfig) implements ChannelRepository.TransportState {
        static State of(Channel c) {
            return new State(c.getId(), c.getActive(), c.getPlatform(), c.getToken(), c.getCredentialId(),
                c.getSupportChatId(), c.getDeliverySettings(), c.getPlatformConfig());
        }
        Channel channel() {
            var c = new Channel();c.setId(getId);c.setActive(getActive);c.setPlatform(getPlatform);c.setToken(getToken);
            c.setCredentialId(getCredentialId);c.setSupportChatId(getSupportChatId);
            c.setDeliverySettings(getDeliverySettings);c.setPlatformConfig(getPlatformConfig);
            return c;
        }
    }

    private static final class Fixture {
        final ChannelRepository channels = mock(ChannelRepository.class);
        final SharedConfigService config = mock(SharedConfigService.class);
        final IntegrationNetworkService network = mock(IntegrationNetworkService.class);
        final HttpClient http = mock(HttpClient.class);
        final HttpResponse<String> response = mock(HttpResponse.class);
        final Channel channel = new Channel();
        final Channel persisted;
        final ChannelTransportService service = new ChannelTransportService(channels, new ObjectMapper(), config, network, org.mockito.Mockito.mock(com.example.panel.service.ChildInfrastructureService.class));
        Fixture(boolean bound) throws Exception {
            channel.setId(ID);channel.setActive(true);channel.setPlatform("telegram");channel.setToken("inline-token");
            channel.setCredentialId(bound ? 7L : null);channel.setSupportChatId("-123");
            channel.setDeliverySettings("{\"broadcast_channel_id\":\"-456\"}");
            channel.setPlatformConfig("{\"bot_api_base_url\":\"https://api.telegram.org\"}");
            persisted = State.of(channel).channel();
            when(channels.findById(ID)).thenReturn(Optional.of(channel));
            when(channels.findTransportStateById(ID)).thenAnswer(call -> Optional.of(State.of(persisted)));
            when(config.loadBotCredentials()).thenReturn(List.of(
                new BotCredential(7L, "selected", "telegram", "bound-token", true),
                new BotCredential(8L, "other", "telegram", "bound-token", true)));
            when(network.createChannelHttpClient(eq(channel), any())).thenReturn(http);
            when(network.resolveTelegramLegacyBotApiBaseUrl(channel)).thenReturn("https://api.telegram.org");
            when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"ok\":true}");
            doReturn(response).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }
        void changePersisted(String change) {
            switch (change) {
                case "missing" -> when(channels.findTransportStateById(ID)).thenReturn(Optional.empty());
                case "query-failure" -> when(channels.findTransportStateById(ID)).thenThrow(new IllegalStateException("test query unavailable"));
                case "null-result" -> when(channels.findTransportStateById(ID)).thenReturn(null);
                default -> mutate(persisted, change);
            }
        }
        boolean execute(String op) {
            if (op.startsWith("test-")) return Boolean.TRUE.equals(service.testChannel(ID,
                Map.of("target_mode", op.equals("test-both") ? "both" : "group", "message", "test")).getBody().get("success"));
            return service.sendConfiguredMessage(ID, op, "777", "test").success();
        }
    }
}
