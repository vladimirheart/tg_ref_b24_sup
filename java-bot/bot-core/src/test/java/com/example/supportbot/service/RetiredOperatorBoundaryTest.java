package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.*;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class RetiredOperatorBoundaryTest {
    @Test void retiredChannelHttpUpdateNeverContactsApi() throws Exception {
        var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{calls.incrementAndGet();exchange.sendResponseHeaders(500,-1);exchange.close();});server.start();
        try {
            var properties=new IntegrationPanelApiProperties();properties.setToken("r295-fake");properties.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
            var client=new PanelChannelClient(properties,new ObjectMapper(),new PanelApiRequestHeadersFactory(properties),new BotRuntimeSenderIdentity(22,"telegram"));
            assertThat(client.updateSupportChatId(22L,"-100222")).isEmpty();assertThat(calls.get()).isZero();
        } finally { server.stop(0); }
    }
    @Test void localChannelUpdateIsDeniedBeforeJpaOrHttp() {
        var repository=mock(ChannelRepository.class);var mode=mock(BotIntegrationTransportMode.class);var api=mock(PanelChannelClient.class);
        var service=new ChannelService(repository,mode,api);var channel=new Channel();channel.setSupportChatId("original");
        assertThatThrownBy(()->service.updateSupportChatId(channel,"-100222")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(channel.getSupportChatId()).isEqualTo("original");verifyNoInteractions(repository,mode,api);
    }
    @Test void localOperatorRelayCannotReachAnyUninitializedStorageOrTransportDependency() {
        var service=mock(TicketService.class,CALLS_REAL_METHODS);
        assertThatThrownBy(()->service.recordOperatorRelay(1001L,"T-22","injected",new Channel(),91L,null,"admin"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThat(service.markOperatorMessageEdited("T-22",91L,"injected","admin")).isFalse();
    }
}
