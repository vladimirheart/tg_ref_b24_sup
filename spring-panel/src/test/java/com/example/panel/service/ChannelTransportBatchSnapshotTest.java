package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChannelTransportBatchSnapshotTest {
    @ParameterizedTest @ValueSource(strings={"support-chat","broadcast-chat","network-route","api-root","unrelated-name"})
    void oneBatchNeverRecapturesChangedRoutingContextAfterFirstRecipient(String change) throws Exception {
        long id=9_007_199_254_740_993L;
        var repository=mock(ChannelRepository.class);var network=mock(IntegrationNetworkService.class);
        var http=mock(HttpClient.class);HttpResponse<String> response=mock(HttpResponse.class);
        var channel=new Channel();channel.setId(id);channel.setActive(true);channel.setPlatform("telegram");
        channel.setToken("test-only");channel.setSupportChatId("-123");
        channel.setDeliverySettings("{\"broadcast_channel_id\":\"-456\"}");
        channel.setPlatformConfig("{\"bot_api_base_url\":\"https://api.telegram.org\"}");
        when(repository.findById(id)).thenReturn(Optional.of(channel));
        // Both the live/cached object and independently returned state change after send #1.
        // A per-recipient recapture would incorrectly authorize the old queued recipient.
        when(repository.findTransportStateById(id)).thenAnswer(call ->
            Optional.of(ChannelTransportPersistedStateTest.State.of(channel)));
        when(network.resolveTelegramLegacyBotApiBaseUrl(channel)).thenReturn("https://api.telegram.org");
        when(network.createChannelHttpClient(eq(channel),any())).thenReturn(http);
        when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"ok\":true}");
        doAnswer(call -> {
            if(change.equals("unrelated-name"))channel.setChannelName("new display name");
            else ChannelTransportPersistedStateTest.mutate(channel,change);
            return response;
        }).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var service=new ChannelTransportService(repository,new ObjectMapper(),mock(SharedConfigService.class),network);
        var body=service.testChannel(id,Map.of("target_mode","both","message","test")).getBody();
        boolean stable=change.equals("unrelated-name");
        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("sent")).isEqualTo(stable?List.of("-123","-456"):List.of("-123"));
        assertThat(body.get("failed")).isEqualTo(stable?List.of():List.of("-456"));
        verify(http,times(stable?2:1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
}
