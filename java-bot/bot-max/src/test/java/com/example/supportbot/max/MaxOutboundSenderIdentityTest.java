package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.service.*;
import com.example.supportbot.entity.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.supportbot.config.MaxBotProperties;

class MaxOutboundSenderIdentityTest {
    private static final long CHANNEL=9_007_199_254_740_993L;
    private final BotRuntimeSenderIdentity runtime=new BotRuntimeSenderIdentity(CHANNEL,"max");
    private final MaxBotProperties properties=new MaxBotProperties();
    private final MaxApiClient provider=spy(new MaxApiClient(properties));
    private void token(String token){properties.setToken(token);}
    @Test void identityComesFromActualProviderCredentialsAndCanonicalRuntime(){
        token("provider-token");
        var messenger=new MaxOutboundMessenger(provider,runtime);
        assertThat(messenger.senderIdentity()).isEqualTo(runtime.forToken("max","provider-token"));
        token("rotated-token");
        assertThat(messenger.senderIdentity()).isEqualTo(runtime.forToken("max","rotated-token"));
    }
    @Test void wrongRuntimePlatformOrMissingCredentialsCannotBorrowTheEventChannel(){
        token("provider-token");
        assertThat(new MaxOutboundMessenger(provider,new BotRuntimeSenderIdentity(CHANNEL,"foreign")).senderIdentity()).isNull();
        token("");
        assertThat(new MaxOutboundMessenger(provider,runtime).senderIdentity()).isNull();
    }
    @Test void admittedIdentityDeliversThroughItsActualAdapter(){
        token("provider-token");
        doReturn(true).when(provider).sendMessageToUser(77L,"payload");
        var admission=mock(PanelOutboundSenderAuthorizationClient.class);when(admission.authorize(any())).thenReturn(true);
        var service=new MessagingService(List.of(new MaxOutboundMessenger(provider,runtime)),admission);
        var channel=new Channel();channel.setId(CHANNEL);channel.setPlatform("max");
        assertThat(service.sendToUser(channel,77L,"payload")).isTrue();
        verify(provider).sendMessageToUser(77L,"payload");
        verify(admission).authorize(runtime.forToken("max","provider-token"));
    }
}
