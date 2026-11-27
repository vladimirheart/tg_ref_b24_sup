package com.example.supportbot.telegram;

import static org.mockito.Mockito.*;
import com.example.supportbot.config.BotProperties;
import com.example.supportbot.service.*;
import com.example.supportbot.settings.BotSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.telegram.telegrambots.meta.api.objects.*;

/** Actual public ingress: no group event can claim a private delivery or invoke business/provider I/O. */
class TelegramPrivateIngressTest {
    SupportBot bot;
    List<Object> boundaries;
    @BeforeEach void setup() {
        var props=new BotProperties();props.setToken("r295-fake");props.setChannelId(22L);
        var blacklist=mock(BlacklistService.class);var unblock=mock(UnblockRequestService.class);
        var attachments=mock(AttachmentService.class);var channels=mock(ChannelService.class);
        var settings=mock(BotSettingsService.class);var tickets=mock(TicketService.class);var history=mock(ChatHistoryService.class);
        var feedback=mock(FeedbackService.class);var ingress=mock(BotIngressCoordinationService.class);
        var guard=mock(BotWebhookDeliveryGuardService.class);var runtime=mock(RuntimeConfigService.class);
        var handler=mock(TelegramQuestionnaireHandler.class);var delivery=mock(SupportChatDeliveryService.class);
        boundaries=List.of(blacklist,unblock,attachments,channels,settings,tickets,history,feedback,ingress,guard,runtime,handler,delivery);
        bot=new SupportBot(props,blacklist,unblock,attachments,channels,settings,tickets,history,feedback,ingress,guard,runtime,handler,new ObjectMapper(),delivery);
        bot.getOptions().setBaseUrl("http://127.0.0.1:1/bot");
    }
    @ParameterizedTest @ValueSource(strings={"group","supergroup","channel","edited-group","configured-id-collision",
        "reply-command","confirm-command","new-members","foreign-private","missing-author","zero-author","missing-chat","empty","null"})
    void nonPrivateOrUnownedEventsNeverReachAnyBoundary(String kind) {
        var user=new User();user.setId(1001L);var chat=new Chat();chat.setId(-100222L);chat.setType("supergroup");
        var message=new Message();message.setChat(chat);message.setFrom(user);message.setMessageId(91);message.setText("#T-foreign private payload");
        var update=new Update();update.setUpdateId(91);update.setMessage(message);
        switch(kind) {
            case "group","supergroup","channel" -> chat.setType(kind);
            case "edited-group" -> { update.setMessage(null);update.setEditedMessage(message); }
            case "configured-id-collision" -> chat.setId(22L);
            case "reply-command" -> message.setText("/reply T-foreign injected reply");
            case "confirm-command" -> message.setText("/confirm");
            case "new-members" -> message.setNewChatMembers(List.of(user));
            case "foreign-private" -> { chat.setType("private");chat.setId(1002L); }
            case "missing-author" -> { chat.setType("private");message.setFrom(null); }
            case "zero-author" -> { chat.setType("private");chat.setId(0L);user.setId(0L); }
            case "missing-chat" -> message.setChat(null);
            case "empty" -> update.setMessage(null);
            case "null" -> update=null;
        }
        bot.onUpdateReceived(update);verifyNoInteractions(boundaries.toArray());
    }
    @Test void rejectedAttachmentHasOneReadableClientNoticeWithoutRawProviderSecrets() throws Exception {
        var sender=spy(bot);
        doReturn(new Message()).when(sender).execute(any(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
        var message=new Message();var chat=new Chat();chat.setId(1001L);chat.setType("private");message.setChat(chat);
        sender.reportAttachmentFailure(message,new java.io.IOException("HTTP 413 token=private https://private.example"),false);
        var notice=org.mockito.ArgumentCaptor.forClass(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class);
        verify(sender).execute(notice.capture());
        org.assertj.core.api.Assertions.assertThat(notice.getValue().getText()).contains("INBOUND_FILE_TOO_LARGE","размера").doesNotContain("private","https://");
        org.assertj.core.api.Assertions.assertThat(notice.getValue().getChatId()).isEqualTo("1001");
        verifyNoInteractions(boundaries.toArray());
    }
    @Test void cleanupFailureAfterAcceptedAttachmentDoesNotAskClientToResend() throws Exception {
        var sender=spy(bot);var message=new Message();var chat=new Chat();chat.setId(1001L);message.setChat(chat);
        sender.reportAttachmentFailure(message,new java.io.IOException("stream close failed"),true);
        verify(sender,never()).execute(any(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
        verifyNoInteractions(boundaries.toArray());
    }
}
