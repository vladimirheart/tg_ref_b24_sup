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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChannelTransportLateCredentialChangeTest {
    static Stream<Arguments> changes() {
        return Stream.of("test","support_chat","broadcast_channel","custom_chat")
            .flatMap(operation -> Stream.of("revoke","rotate","rebind","disable","foreign-id")
                .map(change -> Arguments.of(operation,change)));
    }
    @ParameterizedTest @MethodSource("changes")
    void lateChangeDuringNetworkSetupStopsProviderHandoff(String operation,String change) throws Exception {
        var channels=mock(ChannelRepository.class);var config=mock(SharedConfigService.class);
        var network=mock(IntegrationNetworkService.class);var http=mock(HttpClient.class);
        HttpResponse<String> response=mock(HttpResponse.class);
        var channel=new Channel();channel.setId(9007199254740993L);channel.setActive(true);
        channel.setPlatform("telegram");channel.setToken("retired-inline");channel.setCredentialId(7L);
        channel.setSupportChatId("-123");channel.setDeliverySettings("{\"broadcast_channel_id\":\"-456\"}");
        when(channels.findById(9007199254740993L)).thenReturn(Optional.of(channel));
        when(config.loadBotCredentials()).thenReturn(List.of(new BotCredential(7L,"selected","telegram","selected",true)));
        when(network.resolveTelegramLegacyBotApiBaseUrl(channel)).thenReturn("https://api.telegram.org");
        when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"ok\":true}");
        doReturn(response).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        when(network.createChannelHttpClient(eq(channel),any())).thenAnswer(invocation -> {
            switch(change) {
                case "revoke" -> when(config.loadBotCredentials()).thenReturn(List.of());
                case "rotate" -> when(config.loadBotCredentials()).thenReturn(List.of(new BotCredential(7L,"selected","telegram","rotated",true)));
                case "rebind" -> {channel.setCredentialId(8L);when(config.loadBotCredentials()).thenReturn(List.of(new BotCredential(8L,"selected","telegram","selected",true)));}
                case "disable" -> channel.setActive(false);
                case "foreign-id" -> channel.setId(9007199254740994L);
                default -> throw new AssertionError(change);
            }
            return http;
        });
        var service=new ChannelTransportService(channels,new ObjectMapper(),config,network);
        boolean sent=operation.equals("test")
            ? Boolean.TRUE.equals(service.testChannel(9007199254740993L,Map.of("target_mode","group","message","test")).getBody().get("success"))
            : service.sendConfiguredMessage(9007199254740993L,operation,"777","test").success();
        assertThat(sent).isFalse();
        verify(network,times(1)).createChannelHttpClient(eq(channel),any());
        verify(http,never()).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
}
