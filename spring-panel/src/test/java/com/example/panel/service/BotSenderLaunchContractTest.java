package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

class BotSenderLaunchContractTest {
    static Stream<Arguments> children(){return Stream.of("telegram","vk","max").flatMap(p->Stream.of(42L,9_007_199_254_740_993L).map(id->Arguments.of(p,id)));}
    @ParameterizedTest @MethodSource("children")
    void launcherBindsCanonicalChannelAndPlatformWithoutChangingExternalChatId(String platform,long id){
        var config=mock(SharedConfigService.class);when(config.loadSettings()).thenReturn(Map.of());
        var environment=new MockEnvironment().withProperty("app.datasource.mode","postgresql").withProperty("spring.datasource.url","jdbc:postgresql://isolated/iguana").withProperty("spring.datasource.username","isolated").withProperty("spring.datasource.password","isolated").withProperty("app.integration.transport.mode","rabbitmq");
        // This fixture isolates identity from the independent bounded MAX port allocator.
        var properties=spy(new BotProcessProperties());doReturn(19000).when(properties).resolveMaxPort(any());
        var service=new BotRuntimeContractService(properties,new IntegrationNetworkService(config,new ObjectMapper()),new ObjectMapper(),new PanelDatabaseRuntimeMode(environment),environment);
        var channel=new Channel();channel.setId(id);channel.setPlatform(platform);channel.setSupportChatId("-100");
        var env=service.buildEnvironment(channel,new BotCredential(7L,"selected",platform,"provider-token",true),Path.of("isolated.log"));
        assertThat(env).containsEntry("APP_BOT_CHANNEL_ID",Long.toString(id)).containsEntry("APP_BOT_PLATFORM",platform).containsEntry("GROUP_CHAT_ID","-100")
            .containsEntry("APP_INTEGRATION_RABBITMQ_OUTBOUND_ROUTING_KEY","integration.outbound.feedback.prompt."+platform+".channel."+id);
        assertThat(service.describe(channel,Path.of(".")).requiredEnvironmentKeys()).contains("APP_BOT_CHANNEL_ID","APP_BOT_PLATFORM");
        if(platform.equals("vk"))assertThat(env).containsEntry("VK_OPERATOR_CHAT_ID","-100");
        if(platform.equals("max"))assertThat(env).containsEntry("MAX_CHANNEL_ID",Long.toString(id));
    }
}
