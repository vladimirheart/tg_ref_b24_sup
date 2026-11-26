package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ChannelListNoProviderIoTest {
    @Test void administratorListReadDoesNotRefreshMissingBotMetadataOrWriteChannels() {
        var repository=mock(ChannelRepository.class);
        var config=mock(SharedConfigService.class);
        var network=mock(IntegrationNetworkService.class);
        var infrastructure=mock(ChildInfrastructureService.class);
        var channel=new Channel();channel.setId(3L);channel.setChannelName("Fixture");
        channel.setPlatform("telegram");channel.setToken("fixture-only-no-provider");
        when(repository.findAll()).thenReturn(List.of(channel));
        when(config.loadBotCredentials()).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            "Operator",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),new SimpleGrantedAuthority("PAGE_SETTINGS"))));
        try {
            var service=new ChannelTransportService(repository,new ObjectMapper(),config,network,infrastructure);
            assertThat(service.getChannels().getBody()).containsEntry("success",true);
            verify(repository).findAll();verifyNoMoreInteractions(repository);
            verifyNoInteractions(network,infrastructure);
            assertThat(channel.getBotName()).isNull();assertThat(channel.getBotUsername()).isNull();
        } finally {SecurityContextHolder.clearContext();}
    }
}
