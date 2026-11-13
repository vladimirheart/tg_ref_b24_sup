package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.BotProcessProperties;
import com.example.panel.entity.Channel;
import com.example.panel.repository.ChildInfrastructureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ChildInfrastructureServiceTest {
    @Test void directModeDoesNotProvision() {
        var repository=mock(ChildInfrastructureRepository.class);var broker=mock(ChildRabbitProvisioner.class);
        assertThat(new ChildInfrastructureService(repository,broker,new MockEnvironment()).prepare(null).state()).isEqualTo("not_required");
        verifyNoInteractions(repository,broker);
    }
    @Test void weakMasterAndInvalidChannelNeverProvision() {
        var repository=mock(ChildInfrastructureRepository.class);var broker=mock(ChildRabbitProvisioner.class);
        var service=new ChildInfrastructureService(repository,broker,new MockEnvironment().withProperty("app.integration.transport.mode","rabbitmq"));
        var c=channel();assertThat(service.prepare(c).state()).isEqualTo("failed");
        c.setActive(false);assertThat(service.prepare(c).state()).isEqualTo("failed");
        verifyNoInteractions(repository,broker);
    }
    @Test void sqlErrorsNeverExposeStatementOrCredentials() {
        var repository=mock(ChildInfrastructureRepository.class);var broker=mock(ChildRabbitProvisioner.class);
        when(repository.prepare(any(),anyString(),anyString())).thenThrow(new IllegalStateException("PASSWORD highly-private-secret SQL"));
        var service=new ChildInfrastructureService(repository,broker,new MockEnvironment().withProperty("app.integration.transport.mode","rabbitmq")
            .withProperty("app.bots.internal-api.token","test-only-master-012345678901234567890"));
        var result=service.prepare(channel());
        assertThat(result.state()).isEqualTo("failed");assertThat(result.message()).contains("PostgreSQL").doesNotContain("highly-private-secret","PASSWORD","SQL statement");
        verifyNoInteractions(broker);
    }
    @Test void failedPreflightDoesNotStopExistingProcessOrLaunchAnother() {
        var infrastructure=mock(ChildInfrastructureService.class);
        doThrow(new IllegalStateException("Подготовка не завершена")).when(infrastructure).requireReady(any());
        var contract=mock(BotRuntimeContractService.class);
        var runtime=spy(new BotProcessService(mock(SharedConfigService.class),new BotProcessProperties(),contract,infrastructure));
        var result=runtime.start(channel());
        assertThat(result.running()).isFalse();assertThat(result.message()).contains("Подготовка не завершена");
        verify(runtime,never()).stop(any());verifyNoInteractions(contract);
    }
    static Channel channel(){var c=new Channel();c.setId(1L);c.setPlatform("max");c.setToken("test-only-token");c.setActive(true);return c;}
}
