package com.example.supportbot.telegram;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.*;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.service.*;
import com.example.supportbot.settings.BotSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.telegram.telegrambots.meta.api.objects.*;

/** Real edit handler; SDK/network, configuration and ticket boundary are mocks. */
class TelegramOwnerClientEditTest {
    static final long USER=9_007_199_254_740_993L,CHANNEL=USER+2;
    TicketService tickets;Channel channel;SupportBot bot;
    @BeforeEach void setup(){
        tickets=mock(TicketService.class);channel=new Channel();channel.setId(CHANNEL);channel.setPlatform("telegram");channel.setActive(true);
        var properties=new BotProperties();properties.setToken("r290-test-only");properties.setChannelId(-900077L);
        var channels=mock(ChannelService.class);when(channels.ensurePublicIdForToken("r290-test-only")).thenReturn(channel);
        bot=new SupportBot(properties,mock(BlacklistService.class),mock(UnblockRequestService.class),mock(AttachmentService.class),channels,mock(BotSettingsService.class),tickets,mock(ChatHistoryService.class),mock(FeedbackService.class),mock(BotIngressCoordinationService.class),mock(BotWebhookDeliveryGuardService.class),mock(RuntimeConfigService.class),mock(TelegramQuestionnaireHandler.class),new ObjectMapper());
    }
    Message message(){var user=new User();user.setId(USER);var chat=new Chat();chat.setId(USER);chat.setType("private");var message=new Message();message.setFrom(user);message.setChat(chat);message.setMessageId(Integer.MAX_VALUE);message.setText(" edited Ю ");return message;}
    void edit(Message message){try{var method=SupportBot.class.getDeclaredMethod("handleEditedClientMessage",Message.class);method.setAccessible(true);method.invoke(bot,message);}catch(InvocationTargetException failure){throw new IllegalStateException(failure.getCause());}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    @Test void privateEditPassesItsActualAuthorAndCanonicalPlatform(){
        edit(message());var calls=mockingDetails(tickets).getInvocations();assertThat(calls).hasSize(1);var call=calls.iterator().next();assertThat(call.getMethod().getName()).isEqualTo("markClientMessageEdited");assertThat(call.getArguments()).containsExactly("telegram",CHANNEL,USER,(long)Integer.MAX_VALUE," edited Ю ");
    }
    @ParameterizedTest @ValueSource(strings={"no-author","zero-author","foreign-chat","group","null-channel","zero-provider","blank-text","wrong-platform"})
    void malformedOrForeignClientEditNeverReachesWriteBoundary(String change){
        var message=message();switch(change){case "no-author"->message.setFrom(null);case "zero-author"->message.getFrom().setId(0L);case "foreign-chat"->message.getChat().setId(USER+1);case "group"->message.getChat().setType("group");case "null-channel"->channel.setId(null);case "zero-provider"->message.setMessageId(0);case "blank-text"->message.setText(" ");case "wrong-platform"->channel.setPlatform("max");}edit(message);verifyNoInteractions(tickets);
    }
}
