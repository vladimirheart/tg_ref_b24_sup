package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelCredentialIdAllocationTest {
    @Test void deletedReferenceCannotAccidentallyBindTheNextCreatedCredential() {
        assertNextId(List.of(),7L,8L);
    }
    @Test void referencedIdsAboveJavaScriptSafeIntegerRemainExact() {
        assertNextId(List.of(),9007199254740993L,9007199254740994L);
    }
    @Test void considersBothCatalogAndChannelReferences() {
        assertNextId(List.of(new BotCredential(20L,"old","telegram","old",true)),7L,21L);
    }
    @Test void exhaustedReferencedIdFailsWithoutWritingCatalog() { assertExhausted(List.of(),Long.MAX_VALUE); }
    @Test void exhaustedCatalogIdFailsWithoutWritingCatalog() {
        assertExhausted(List.of(new BotCredential(Long.MAX_VALUE,"old","telegram","old",true)),7L);
    }
    private void assertNextId(List<BotCredential> catalog,long reference,long expected) {
        var channels=mock(ChannelRepository.class);var config=mock(SharedConfigService.class);
        var channel=new Channel();channel.setId(1L);channel.setCredentialId(reference);channel.setActive(false);
        when(channels.findAll()).thenReturn(List.of(channel));when(config.loadBotCredentials()).thenReturn(catalog);
        var service=new ChannelTransportService(channels,new ObjectMapper(),config,mock(IntegrationNetworkService.class), org.mockito.Mockito.mock(com.example.panel.service.ChildInfrastructureService.class));
        var response=service.createBotCredential(Map.of("name","new","token","new-token","platform","telegram"));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?,?>)response.getBody().get("credential")).get("id")).isEqualTo(expected);
        var saved=org.mockito.ArgumentCaptor.forClass(List.class);verify(config).saveBotCredentials(saved.capture());
        assertThat(((BotCredential)saved.getValue().get(saved.getValue().size()-1)).id()).isEqualTo(expected);
        assertThat(channel.getCredentialId()).isEqualTo(reference);
        verify(channels,never()).save(any());verify(channels,never()).saveAll(any());
    }
    private void assertExhausted(List<BotCredential> catalog,long reference) {
        var channels=mock(ChannelRepository.class);var config=mock(SharedConfigService.class);
        var channel=new Channel();channel.setCredentialId(reference);
        when(channels.findAll()).thenReturn(List.of(channel));when(config.loadBotCredentials()).thenReturn(catalog);
        var service=new ChannelTransportService(channels,new ObjectMapper(),config,mock(IntegrationNetworkService.class), org.mockito.Mockito.mock(com.example.panel.service.ChildInfrastructureService.class));
        var response=service.createBotCredential(Map.of("name","new","token","new-token","platform","telegram"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        verify(config,never()).saveBotCredentials(any());
        assertThat(channel.getCredentialId()).isEqualTo(reference);
    }
}
