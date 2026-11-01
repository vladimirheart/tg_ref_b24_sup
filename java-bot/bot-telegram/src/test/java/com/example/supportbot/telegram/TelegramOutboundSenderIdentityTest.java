package com.example.supportbot.telegram;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.service.*;
import com.example.supportbot.entity.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelegramOutboundSenderIdentityTest {
    private static final long CHANNEL=9_007_199_254_740_993L;
    private final BotRuntimeSenderIdentity runtime=new BotRuntimeSenderIdentity(CHANNEL,"telegram");
    private final SupportBot provider=mock(SupportBot.class);
    private void token(String token){when(provider.getBotToken()).thenReturn(token);}
    @Test void identityComesFromActualProviderCredentialsAndCanonicalRuntime(){
        token("provider-token");
        var messenger=new TelegramOutboundMessenger(provider,runtime);
        assertThat(messenger.senderIdentity()).isEqualTo(runtime.forToken("telegram","provider-token"));
        token("rotated-token");
        assertThat(messenger.senderIdentity()).isEqualTo(runtime.forToken("telegram","rotated-token"));
    }
    @Test void wrongRuntimePlatformOrMissingCredentialsCannotBorrowTheEventChannel(){
        token("provider-token");
        assertThat(new TelegramOutboundMessenger(provider,new BotRuntimeSenderIdentity(CHANNEL,"foreign")).senderIdentity()).isNull();
        token("");
        assertThat(new TelegramOutboundMessenger(provider,runtime).senderIdentity()).isNull();
    }
    @Test void admittedIdentityDeliversThroughItsActualAdapter(){
        token("provider-token");
        when(provider.sendDirectMessage(77L,"payload")).thenReturn(true);
        var admission=mock(PanelOutboundSenderAuthorizationClient.class);when(admission.authorize(any())).thenReturn(true);
        var service=new MessagingService(List.of(new TelegramOutboundMessenger(provider,runtime)),admission);
        var channel=new Channel();channel.setId(CHANNEL);channel.setPlatform("telegram");
        assertThat(service.sendToUser(channel,77L,"payload")).isTrue();
        verify(provider).sendDirectMessage(77L,"payload");
        verify(admission).authorize(runtime.forToken("telegram","provider-token"));
    }
}
