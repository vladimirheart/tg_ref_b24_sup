package com.example.supportbot.telegram;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.BotProperties;
import com.example.supportbot.entity.*;
import com.example.supportbot.service.*;
import com.example.supportbot.settings.BotSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/** Actual legacy bot notification and media methods; panel decisions/SDK/file store are doubles. */
class TelegramSupportChatDeliveryTest {
    Channel channel;
    BotProperties properties;
    PanelOutboundSenderAuthorizationClient authorization;
    AttachmentService attachments;
    UnblockRequestService requests;
    SupportBot bot;
    HttpServer sdk;
    AtomicInteger photoStatus = new AtomicInteger(200);
    AtomicReference<Throwable> httpFailure = new AtomicReference<>();
    Runnable afterPhoto = () -> {};
    boolean dropPhotoResponse;
    List<String> recipients = new ArrayList<>();
    List<String> methods = new ArrayList<>();
    AtomicBoolean allowed = new AtomicBoolean(true);
    @org.junit.jupiter.api.io.TempDir Path temporary;
    Path materialized;

    @BeforeEach void setup() throws Exception {
        channel = new Channel(); channel.setId(22L); channel.setPlatform("telegram"); channel.setActive(true);
        channel.setSupportChatId("-1001234567890");
        properties = new BotProperties(); properties.setToken("r294-fake-telegram"); properties.setChannelId(22L);
        authorization = mock(PanelOutboundSenderAuthorizationClient.class);
        when(authorization.authorize(any())).thenAnswer(call -> allowed.get());
        when(authorization.authorizeSupportChat(any(), anyString())).thenAnswer(call -> allowed.get());
        var delivery = new SupportChatDeliveryService(new BotRuntimeSenderIdentity(22, "telegram"), authorization);
        var channels = mock(ChannelService.class); when(channels.ensurePublicIdForToken(properties.getToken())).thenReturn(channel);
        var coordination = mock(BotIngressCoordinationService.class); when(coordination.isCurrentOwner("telegram", 22L)).thenReturn(true);
        requests = mock(UnblockRequestService.class); when(requests.countPending()).thenReturn(1L);
        when(requests.findRecentPending(3)).thenReturn(List.of());
        attachments = mock(AttachmentService.class);
        materialized = Files.writeString(temporary.resolve("fake.txt"), "r294 fake attachment");
        when(attachments.materialize("owned/test.txt")).thenReturn(materialized);
        bot = new SupportBot(properties, mock(BlacklistService.class), requests, attachments, channels,
            mock(BotSettingsService.class), mock(TicketService.class), mock(ChatHistoryService.class), mock(FeedbackService.class),
            coordination, mock(BotWebhookDeliveryGuardService.class), mock(RuntimeConfigService.class),
            mock(TelegramQuestionnaireHandler.class), new ObjectMapper(), delivery);
        sdk = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sdk.createContext("/bot", exchange -> {
            int status = 200; String response;
            try {
                String method = exchange.getRequestURI().getPath().toLowerCase(Locale.ROOT);
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String recipient;
                if (body.stripLeading().startsWith("{")) recipient = new ObjectMapper().readTree(body).path("chat_id").asText();
                else {
                    var match = Pattern.compile("name=\"chat_id\"[^\r\n]*\r\n(?:[^\r\n]+\r\n)*\r\n(-?[0-9]+)\r\n").matcher(body);
                    assertThat(match.find()).isTrue(); recipient = match.group(1);
                }
                recipients.add(recipient);
                if (method.endsWith("/sendphoto")) { methods.add("photo"); status = photoStatus.get(); afterPhoto.run();
                    if (dropPhotoResponse) { exchange.close(); return; } }
                else if (method.endsWith("/senddocument")) methods.add("document");
                else { assertThat(method).endsWith("/sendmessage"); methods.add("text"); }
                response = status == 200 ? "{\"ok\":true,\"result\":{\"message_id\":1,\"date\":0,\"chat\":{\"id\":" + recipient + ",\"type\":\"supergroup\"}}}"
                    : "{\"ok\":false,\"error_code\":" + status + ",\"description\":\"fake media failure\"}";
            } catch (Throwable failure) { httpFailure.set(failure); status = 500; response = "{}"; }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        }); sdk.start();
        bot.getOptions().setBaseUrl("http://127.0.0.1:" + sdk.getAddress().getPort() + "/bot");
    }

    @AfterEach void stop() { if (sdk != null) sdk.stop(0); assertThat(httpFailure.get()).isNull(); }

    void notify(String kind) {
        switch (kind) {
            case "summary" -> ReflectionTestUtils.invokeMethod(bot, "sendTicketSummaryToOperatorChannel", "private summary");
            case "digest" -> bot.sendUnblockDigest();
            case "unblock" -> {
                var request = new ClientUnblockRequest(); request.setUserId("1001"); request.setReason("private reason");
                ReflectionTestUtils.invokeMethod(bot, "notifyOperatorsAboutUnblockRequest", request);
            }
            case "text", "media" -> ReflectionTestUtils.invokeMethod(bot, "relayActiveMessageToOperators", "T-22", "photo",
                "private client message", kind.equals("media") ? "owned/test.txt" : null, "client", 1001L);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"summary", "digest", "unblock", "text", "media"})
    void allOperatorNotificationsUseAuthorizedSignedGroupInsteadOfDatabaseChannelId(String kind) {
        notify(kind); assertThat(recipients).containsExactly("-1001234567890");
        verify(authorization, atLeastOnce()).authorizeSupportChat(any(), eq("-1001234567890"));
    }

    @ParameterizedTest @ValueSource(strings = {"summary", "digest", "unblock", "text", "media"})
    void deniedNotificationsNeverReachSdkOrMaterializeFiles(String kind) throws Exception {
        allowed.set(false); notify(kind); assertThat(recipients).isEmpty();
        verify(attachments, never()).materialize(anyString());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"0", " 123", "bad-chat"})
    void missingRecipientNeverFallsBackToPropertiesChannelId(String recipient) {
        channel.setSupportChatId(recipient); notify("summary"); notify("media");
        assertThat(recipients).isEmpty(); verifyNoInteractions(authorization);
    }

    @ParameterizedTest @ValueSource(strings = {"backend-revoke", "local-rebind", "inactive", "platform"})
    void mediaRechecksAfterMaterializationAndCleansTemporaryFile(String change) throws Exception {
        when(attachments.materialize("owned/test.txt")).thenAnswer(call -> {
            switch (change) {
                case "backend-revoke" -> allowed.set(false);
                case "local-rebind" -> channel.setSupportChatId("-100999");
                case "inactive" -> channel.setActive(false);
                case "platform" -> channel.setPlatform("vk");
            }
            return materialized;
        });
        notify("media"); assertThat(recipients).isEmpty(); verify(attachments).cleanupMaterialized(materialized);
    }

    @Test void successfulPreferredMediaAlwaysCleansTemporaryFile() throws Exception {
        notify("media"); assertThat(methods).containsExactly("photo"); verify(attachments).cleanupMaterialized(materialized);
    }

    @Test void documentFallbackUsesTheSameAuthorizedRecipient() throws Exception {
        photoStatus.set(400);
        notify("media"); assertThat(methods).containsExactly("photo", "document");
        assertThat(recipients).containsExactly("-1001234567890", "-1001234567890");
        verify(authorization, times(3)).authorizeSupportChat(any(), eq("-1001234567890"));
        verify(attachments).cleanupMaterialized(materialized);
    }

    @Test void revocationAfterPreferredFailurePreventsDocumentAndTextFallback() throws Exception {
        photoStatus.set(400); afterPhoto = () -> allowed.set(false);
        notify("media"); assertThat(recipients).containsExactly("-1001234567890");
        assertThat(methods).containsExactly("photo"); verify(attachments).cleanupMaterialized(materialized);
    }

    @ParameterizedTest @ValueSource(ints = {403, 429, 500, 503})
    void uncertainOrOtherProviderFailureNeverTriggersAutomaticDocumentOrTextRetry(int status) throws Exception {
        photoStatus.set(status); notify("media");
        assertThat(methods).containsExactly("photo"); assertThat(recipients).containsExactly("-1001234567890");
        verify(attachments).cleanupMaterialized(materialized);
    }

    @Test void lostHttpResponseAfterProviderReceivedMediaDoesNotResend() throws Exception {
        dropPhotoResponse = true; notify("media");
        assertThat(methods).containsExactly("photo"); assertThat(recipients).containsExactly("-1001234567890");
        verify(attachments).cleanupMaterialized(materialized);
    }

    @Test void unavailableAuthorizationNeverFallsThroughToProviderOrStorage() throws Exception {
        when(authorization.authorize(any())).thenThrow(new IllegalStateException("fake backend unavailable"));
        notify("media"); assertThat(recipients).isEmpty(); verify(attachments, never()).materialize(anyString());
        verify(authorization).authorize(any());
    }
}
