package com.example.supportbot.vk;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.VkBotProperties;
import com.example.supportbot.entity.*;
import com.example.supportbot.service.*;
import com.example.supportbot.settings.BotSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.queries.messages.MessagesSendQueryWithDeprecated;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;

class VkSupportChatDeliveryTest {
    Channel channel;
    VkBotProperties properties;
    PanelOutboundSenderAuthorizationClient authorization;
    VkApiClient sdk;
    MessagesSendQueryWithDeprecated send;
    VkSupportBot bot;
    List<Long> recipients = new ArrayList<>();
    AtomicBoolean allowed = new AtomicBoolean(true);

    @BeforeEach void setup() throws Exception {
        channel = new Channel(); channel.setId(22L); channel.setPlatform("vk"); channel.setActive(true);
        channel.setSupportChatId("2000000011");
        properties = new VkBotProperties(); properties.setToken("r294-fake-vk"); properties.setChannelId(22L);
        properties.setGroupId(1); properties.setEnabled(true);
        authorization = mock(PanelOutboundSenderAuthorizationClient.class);
        when(authorization.authorize(any())).thenAnswer(call -> allowed.get());
        when(authorization.authorizeSupportChat(any(), anyString())).thenAnswer(call -> allowed.get());
        var delivery = new SupportChatDeliveryService(new BotRuntimeSenderIdentity(22, "vk"), authorization);
        var channels = mock(ChannelService.class); when(channels.ensurePublicIdForToken(properties.getToken(), "VK", "vk")).thenReturn(channel);
        var coordination = mock(BotIngressCoordinationService.class);
        when(coordination.tryAcquireOrRenewJob(eq("vk"), eq(22L), anyString())).thenReturn(true);
        var requests = mock(UnblockRequestService.class); when(requests.countPending()).thenReturn(1L);
        when(requests.findRecentPending(3)).thenReturn(List.of());
        bot = new VkSupportBot(properties, mock(BlacklistService.class), requests, mock(AttachmentService.class), channels,
            mock(BotSettingsService.class), mock(TicketService.class), mock(ChatHistoryService.class), mock(FeedbackService.class),
            coordination, mock(BotSessionStoreService.class), mock(BotWebhookDeliveryGuardService.class), mock(RuntimeConfigService.class),
            new ObjectMapper(), delivery);
        sdk = mock(VkApiClient.class, RETURNS_DEEP_STUBS);
        send = mock(MessagesSendQueryWithDeprecated.class, RETURNS_SELF);
        when(sdk.messages().sendDeprecated(any(GroupActor.class))).thenReturn(send);
        when(send.peerId(anyLong())).thenAnswer(call -> { recipients.add(call.getArgument(0)); return send; });
        when(send.execute()).thenReturn(123);
        ReflectionTestUtils.setField(bot, "vkClient", sdk);
    }

    @AfterEach void stop() { bot.stop(); }

    void notify(String kind) {
        switch (kind) {
            case "summary" -> ReflectionTestUtils.invokeMethod(bot, "sendOperatorMessage", "private summary");
            case "digest" -> bot.sendUnblockDigest();
            case "unblock" -> {
                var request = new ClientUnblockRequest(); request.setUserId("1001"); request.setReason("private reason");
                ReflectionTestUtils.invokeMethod(bot, "notifyOperatorsAboutUnblockRequest", new GroupActor(1L, "fake"), request);
            }
            case "relay" -> ReflectionTestUtils.invokeMethod(bot, "relayActiveMessageToOperators", new GroupActor(1L, "fake"),
                "T-22", "private client text", 1001L, true, null);
        }
    }

    static Stream<Arguments> notifications() {
        return Stream.of("summary", "digest", "unblock", "relay")
            .flatMap(kind -> Stream.of(true, false).map(allow -> Arguments.of(kind, allow)));
    }

    @ParameterizedTest @MethodSource("notifications")
    void everyLegacyNotificationRequiresTheActualSupportPeer(String kind, boolean allow) throws Exception {
        allowed.set(allow); notify(kind);
        assertThat(recipients).containsExactly(allow ? new Long[]{2000000011L} : new Long[0]);
        if (allow) { verify(send).execute(); verify(authorization).authorizeSupportChat(any(), eq("2000000011")); }
        else verify(send, never()).execute();
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"0", "-100", "+123", "00123", "2147483648", "bad-chat"})
    void missingOrInvalidPeerNeverFallsBackToDatabaseChannelId(String recipient) {
        channel.setSupportChatId(recipient); notify("summary");
        assertThat(recipients).isEmpty(); verifyNoInteractions(authorization);
    }

    @Test void localRebindDuringAdmissionDoesNotRerouteOrSend() throws Exception {
        when(authorization.authorizeSupportChat(any(), anyString())).thenAnswer(call -> { channel.setSupportChatId("2000000012"); return true; });
        notify("summary"); assertThat(recipients).isEmpty(); verify(send, never()).execute();
    }

    @Test void unavailableAuthorizationDoesNotReachSdk() throws Exception {
        when(authorization.authorize(any())).thenThrow(new IllegalStateException("fake API unavailable"));
        notify("unblock"); assertThat(recipients).isEmpty(); verify(send, never()).execute();
    }
}
