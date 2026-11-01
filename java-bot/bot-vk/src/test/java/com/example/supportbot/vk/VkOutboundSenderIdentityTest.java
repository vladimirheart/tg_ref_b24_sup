package com.example.supportbot.vk;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.service.*;
import com.example.supportbot.entity.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.supportbot.config.VkBotProperties;
import org.springframework.test.util.ReflectionTestUtils;

class VkOutboundSenderIdentityTest {
    private static final long CHANNEL=9_007_199_254_740_993L;
    private final BotRuntimeSenderIdentity runtime=new BotRuntimeSenderIdentity(CHANNEL,"vk");
    private final VkBotProperties properties=new VkBotProperties();
    private final VkSupportBot provider=mock(VkSupportBot.class,CALLS_REAL_METHODS);
    private void token(String token){properties.setToken(token);ReflectionTestUtils.setField(provider,"properties",properties);}
    @Test void identityComesFromActualProviderCredentialsAndCanonicalRuntime(){
        token("provider-token");
        var messenger=new VkOutboundMessenger(provider,runtime);
        assertThat(messenger.senderIdentity()).isEqualTo(runtime.forToken("vk","provider-token"));
        token("rotated-token");
        assertThat(messenger.senderIdentity()).isEqualTo(runtime.forToken("vk","rotated-token"));
    }
    @Test void wrongRuntimePlatformOrMissingCredentialsCannotBorrowTheEventChannel(){
        token("provider-token");
        assertThat(new VkOutboundMessenger(provider,new BotRuntimeSenderIdentity(CHANNEL,"foreign")).senderIdentity()).isNull();
        token("");
        assertThat(new VkOutboundMessenger(provider,runtime).senderIdentity()).isNull();
    }
    @Test void admittedIdentityDeliversThroughItsActualAdapter(){
        token("provider-token");
        doReturn(true).when(provider).sendDirectMessage(77,"payload");
        var admission=mock(PanelOutboundSenderAuthorizationClient.class);when(admission.authorize(any())).thenReturn(true);
        var service=new MessagingService(List.of(new VkOutboundMessenger(provider,runtime)),admission);
        var channel=new Channel();channel.setId(CHANNEL);channel.setPlatform("vk");
        assertThat(service.sendToUser(channel,77L,"payload")).isTrue();
        verify(provider).sendDirectMessage(77,"payload");
        verify(admission).authorize(runtime.forToken("vk","provider-token"));
    }
}
