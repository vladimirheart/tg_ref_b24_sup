package com.example.supportbot.telegram;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.service.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

class TelegramProviderReceiptTest {
    @ParameterizedTest @ValueSource(strings={"ok","null-message","wrong-chat","null-chat","null-id","zero-id","exception"})
    void capturesOnlyTheRequestedPeerAndPositiveSdkMessageId(String change)throws Exception{
        SupportBot bot=mock(SupportBot.class,CALLS_REAL_METHODS);
        var reply=new Message();var chat=new Chat();chat.setId(change.equals("wrong-chat")?78L:77L);
        reply.setChat(change.equals("null-chat")?null:chat);reply.setMessageId(change.equals("null-id")?null:change.equals("zero-id")?0:42);
        if(change.equals("exception"))doThrow(new TelegramApiException("test failure")).when(bot).execute(any(SendMessage.class));
        else doReturn(change.equals("null-message")?null:reply).when(bot).execute(any(SendMessage.class));
        var provider=new TelegramOutboundMessenger(bot,new BotRuntimeSenderIdentity(1L,"telegram"));
        var result=provider.sendToUserWithReceipt(77L,"Ю test");
        assertThat(result.accepted()).isEqualTo(change.equals("ok"));
        assertThat(result.messageId()).isEqualTo(change.equals("ok")?"42":null);
        var request=org.mockito.ArgumentCaptor.forClass(SendMessage.class);verify(bot,times(1)).execute(request.capture());
        assertThat(request.getValue().getChatId()).isEqualTo("77");assertThat(request.getValue().getText()).isEqualTo("Ю test");
        verify(bot,never()).sendDirectMessage(anyLong(),anyString());
    }
}
