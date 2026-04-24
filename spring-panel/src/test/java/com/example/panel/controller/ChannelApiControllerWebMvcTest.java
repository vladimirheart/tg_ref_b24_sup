package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.IntegrationNetworkService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChannelApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChannelApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChannelRepository channelRepository;

    @MockBean
    private SharedConfigService sharedConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IntegrationNetworkService integrationNetworkService;

    @Test
    void regeneratePublicIdSupportsChannelsRouteAlias() throws Exception {
        Channel channel = new Channel();
        channel.setId(55L);
        channel.setPublicId("old_public_id");

        when(channelRepository.findById(55L)).thenReturn(Optional.of(channel));
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());

        mockMvc.perform(post("/api/channels/55/public-id/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.public_id").isNotEmpty())
                .andExpect(jsonPath("$.channel.id").value(55));
    }

    @Test
    void regeneratePublicIdReturnsNotFoundWhenChannelIsMissing() throws Exception {
        when(channelRepository.findById(505L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/channels/505/public-id/regenerate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Канал не найден"));
    }

    @Test
    void createChannelPersistsVkPlatformConfigAndTemplateSelections() throws Exception {
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            channel.setId(42L);
            return channel;
        });
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());

        mockMvc.perform(post("/api/channels")
                        .contentType("application/json")
                        .content("""
                                {
                                  "channel_name": "VK Support",
                                  "platform": "vk",
                                  "token": "vk-access-token",
                                  "question_template_id": "q-template",
                                  "rating_template_id": "r-template",
                                  "auto_action_template_id": "auto-template",
                                  "platform_config": {
                                    "group_id": 123,
                                    "confirmation_token": "vk-confirm",
                                    "secret": "vk-secret"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.channel.id").value(42))
                .andExpect(jsonPath("$.channel.platform").value("vk"))
                .andExpect(jsonPath("$.channel.public_id").isNotEmpty())
                .andExpect(jsonPath("$.channel.question_template_id").value("q-template"))
                .andExpect(jsonPath("$.channel.rating_template_id").value("r-template"))
                .andExpect(jsonPath("$.channel.auto_action_template_id").value("auto-template"));

        verify(channelRepository).save(argThat(channel ->
                "VK Support".equals(channel.getChannelName())
                        && "vk".equals(channel.getPlatform())
                        && channel.getPublicId() != null
                        && !channel.getPublicId().isBlank()
                        && "q-template".equals(channel.getQuestionTemplateId())
                        && "r-template".equals(channel.getRatingTemplateId())
                        && "auto-template".equals(channel.getAutoActionTemplateId())
                        && "{\"group_id\":123,\"confirmation_token\":\"vk-confirm\",\"secret\":\"vk-secret\"}".equals(channel.getPlatformConfig())
        ));
    }

    @Test
    void createChannelRejectsMissingName() throws Exception {
        mockMvc.perform(post("/api/channels")
                        .contentType("application/json")
                        .content("""
                                {
                                  "platform": "telegram",
                                  "token": "telegram-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Название канала обязательно"));
    }

    @Test
    void createChannelRejectsTelegramWithoutToken() throws Exception {
        mockMvc.perform(post("/api/channels")
                        .contentType("application/json")
                        .content("""
                                {
                                  "channel_name": "TG Missing Token",
                                  "platform": "telegram",
                                  "token": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Для Telegram необходимо указать токен бота."));
    }

    @Test
    void createChannelRejectsVkWithoutCallbackConfiguration() throws Exception {
        mockMvc.perform(post("/api/channels")
                        .contentType("application/json")
                        .content("""
                                {
                                  "channel_name": "VK Missing Callback",
                                  "platform": "vk",
                                  "token": "vk-token",
                                  "platform_config": {
                                    "group_id": 0
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Для VK укажите корректный ID сообщества (group_id)."));
    }

    @Test
    void createChannelRejectsMaxWithoutToken() throws Exception {
        mockMvc.perform(post("/api/channels")
                        .contentType("application/json")
                        .content("""
                                {
                                  "channel_name": "MAX Missing Token",
                                  "platform": "max",
                                  "token": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Для MAX необходимо указать bot token."));
    }

    @Test
    void createChannelNormalizesBlankPlatformToTelegramAndGeneratesDefaults() throws Exception {
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            channel.setId(43L);
            return channel;
        });
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());

        mockMvc.perform(post("/api/channels")
                        .contentType("application/json")
                        .content("""
                                {
                                  "channel_name": "Default TG",
                                  "platform": "",
                                  "token": "telegram-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.channel.id").value(43))
                .andExpect(jsonPath("$.channel.platform").value("telegram"))
                .andExpect(jsonPath("$.channel.public_id").isNotEmpty())
                .andExpect(jsonPath("$.channel.questions_cfg").isMap())
                .andExpect(jsonPath("$.channel.delivery_settings").isMap());

        verify(channelRepository).save(argThat(channel ->
                "Default TG".equals(channel.getChannelName())
                        && "telegram".equals(channel.getPlatform())
                        && "telegram-token".equals(channel.getToken())
                        && "{}".equals(channel.getQuestionsCfg())
                        && "{}".equals(channel.getDeliverySettings())
                        && channel.getPublicId() != null
                        && !channel.getPublicId().isBlank()
        ));
    }

    @Test
    void patchChannelUpdatesCredentialRouteAndPlatformConfig() throws Exception {
        Channel channel = new Channel();
        channel.setId(44L);
        channel.setChannelName("TG Support");
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setDeliverySettings("{}");
        channel.setQuestionsCfg("{}");
        channel.setPublicId("public-44");
        channel.setActive(true);

        when(channelRepository.findById(44L)).thenReturn(Optional.of(channel));
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(9L, "TG Main", "telegram", "token-9", true)
        ));

        mockMvc.perform(post("/api/channels/44")
                        .contentType("application/json")
                        .content("""
                                {
                                  "credential_id": 9,
                                  "support_chat_id": "ops-room",
                                  "network_route": {
                                    "mode": "proxy",
                                    "target": "corp-gateway"
                                  },
                                  "delivery_settings": {
                                    "broadcast_channel_id": "987654"
                                  },
                                  "platform_config": {
                                    "webhook_secret": "tg-secret"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.channel.credential_id").value(9))
                .andExpect(jsonPath("$.channel.credential.id").value(9))
                .andExpect(jsonPath("$.channel.support_chat_id").value("ops-room"))
                .andExpect(jsonPath("$.channel.delivery_settings.broadcast_channel_id").value("987654"))
                .andExpect(jsonPath("$.channel.network_route.mode").value("proxy"))
                .andExpect(jsonPath("$.channel.network_route.target").value("corp-gateway"))
                .andExpect(jsonPath("$.channel.platform_config.webhook_secret").value("tg-secret"));

        verify(channelRepository).save(argThat(saved ->
                Long.valueOf(9L).equals(saved.getCredentialId())
                        && "ops-room".equals(saved.getSupportChatId())
                        && saved.getPlatformConfig() != null
                        && saved.getPlatformConfig().contains("tg-secret")
                        && saved.getDeliverySettings() != null
                        && saved.getDeliverySettings().contains("broadcast_channel_id")
                        && saved.getDeliverySettings().contains("network_route")
        ));
    }

    @Test
    void patchChannelRejectsInvalidQuestionsConfigContract() throws Exception {
        Channel channel = new Channel();
        channel.setId(45L);
        channel.setChannelName("Invalid Config");
        channel.setPlatform("telegram");
        channel.setToken("tg-token");

        when(channelRepository.findById(45L)).thenReturn(Optional.of(channel));

        mockMvc.perform(post("/api/channels/45")
                        .contentType("application/json")
                        .content("""
                                {
                                  "questions_cfg": "broken"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("questions_cfg должен быть объектом или массивом"));
    }

    @Test
    void patchChannelRejectsWhenPayloadHasNoUpdatableFields() throws Exception {
        Channel channel = new Channel();
        channel.setId(451L);
        channel.setChannelName("No Updates");
        channel.setPlatform("telegram");
        channel.setToken("tg-token");

        when(channelRepository.findById(451L)).thenReturn(Optional.of(channel));

        mockMvc.perform(post("/api/channels/451")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Нет полей для обновления"));
    }

    @Test
    void putChannelReturnsNotFoundWhenChannelIsMissing() throws Exception {
        when(channelRepository.findById(404L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/channels/404")
                        .contentType("application/json")
                        .content("""
                                {
                                  "channel_name": "Missing"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Канал не найден"));
    }

    @Test
    void postChannelAliasUpdatesDescriptionAndActiveState() throws Exception {
        Channel channel = new Channel();
        channel.setId(47L);
        channel.setChannelName("Alias Update");
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setDeliverySettings("{}");
        channel.setQuestionsCfg("{}");
        channel.setPublicId("public-47");
        channel.setActive(true);

        when(channelRepository.findById(47L)).thenReturn(Optional.of(channel));
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());

        mockMvc.perform(post("/api/channels/47")
                        .contentType("application/json")
                        .content("""
                                {
                                  "description": "Updated via alias",
                                  "is_active": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.channel.description").value("Updated via alias"))
                .andExpect(jsonPath("$.channel.is_active").value(false));

        verify(channelRepository).save(argThat(saved ->
                "Updated via alias".equals(saved.getDescription())
                        && Boolean.FALSE.equals(saved.getActive())
        ));
    }

    @Test
    void deleteChannelRemovesExistingChannel() throws Exception {
        Channel channel = new Channel();
        channel.setId(46L);
        channel.setChannelName("Delete Me");

        when(channelRepository.findById(46L)).thenReturn(Optional.of(channel));

        mockMvc.perform(delete("/api/channels/46"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(channelRepository).delete(channel);
    }

    @Test
    void deleteChannelReturnsNotFoundWhenChannelIsMissing() throws Exception {
        when(channelRepository.findById(406L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/channels/406"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Канал не найден"));
    }

    @Test
    void testChannelReturnsNotFoundWhenChannelIsMissing() throws Exception {
        when(channelRepository.findById(501L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/channels/501/test-message")
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": "ping"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Канал не найден"));
    }

    @Test
    void testChannelRejectsNonTelegramPlatform() throws Exception {
        Channel channel = new Channel();
        channel.setId(61L);
        channel.setPlatform("vk");
        channel.setToken("vk-token");

        when(channelRepository.findById(61L)).thenReturn(Optional.of(channel));

        mockMvc.perform(post("/api/channels/61/test-message")
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": "ping"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Тестовая отправка доступна только для Telegram"));
    }

    @Test
    void testChannelRejectsWhenTelegramTokenIsMissing() throws Exception {
        Channel channel = new Channel();
        channel.setId(611L);
        channel.setPlatform("telegram");
        channel.setToken("");

        when(channelRepository.findById(611L)).thenReturn(Optional.of(channel));

        mockMvc.perform(post("/api/channels/611/test-message")
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": "ping"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("У канала не задан токен"));
    }

    @Test
    void testChannelRejectsWhenMessageIsMissing() throws Exception {
        Channel channel = new Channel();
        channel.setId(612L);
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setSupportChatId("group-1");

        when(channelRepository.findById(612L)).thenReturn(Optional.of(channel));

        mockMvc.perform(post("/api/channels/612/test-message")
                        .contentType("application/json")
                        .content("""
                                {
                                  "target_mode": "group"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Нужно указать текст сообщения"));
    }

    @Test
    void testChannelRejectsWhenRecipientsCannotBeResolved() throws Exception {
        Channel channel = new Channel();
        channel.setId(62L);
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setDeliverySettings("{}");

        when(channelRepository.findById(62L)).thenReturn(Optional.of(channel));

        mockMvc.perform(post("/api/channels/62/test-message")
                        .contentType("application/json")
                        .content("""
                                {
                                  "target_mode": "both",
                                  "message": "ping"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Не найден получатель. Укажите ID вручную или настройте группу/канал."));
    }

    @Test
    void testChannelSendsToGroupAndBroadcastRecipientsWithDeduplication() throws Exception {
        Channel channel = new Channel();
        channel.setId(63L);
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setSupportChatId("group-1");
        channel.setDeliverySettings("""
                {
                  "broadcast_channel_id": "channel-1"
                }
                """);

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        HttpClient httpClient = mock(HttpClient.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "ok": true
                }
                """);
        when(channelRepository.findById(63L)).thenReturn(Optional.of(channel));
        when(integrationNetworkService.createChannelHttpClient(any(Channel.class), any())).thenReturn(httpClient);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        mockMvc.perform(post("/api/channels/63/test-message")
                        .contentType("application/json")
                        .content("""
                                {
                                  "target_mode": "both",
                                  "recipient": "group-1",
                                  "message": "hello operators"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sent.length()").value(2))
                .andExpect(jsonPath("$.sent[0]").value("group-1"))
                .andExpect(jsonPath("$.sent[1]").value("channel-1"))
                .andExpect(jsonPath("$.failed.length()").value(0));
    }

    @Test
    void testChannelReturnsBadRequestWhenAllDeliveriesFail() throws Exception {
        Channel channel = new Channel();
        channel.setId(64L);
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setSupportChatId("group-2");
        channel.setDeliverySettings("""
                {
                  "broadcast_channel_id": "channel-2"
                }
                """);

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        HttpClient httpClient = mock(HttpClient.class);
        when(response.statusCode()).thenReturn(500);
        when(channelRepository.findById(64L)).thenReturn(Optional.of(channel));
        when(integrationNetworkService.createChannelHttpClient(any(Channel.class), any())).thenReturn(httpClient);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        mockMvc.perform(post("/api/channels/64/test-message")
                        .contentType("application/json")
                        .content("""
                                {
                                  "target_mode": "both",
                                  "message": "hello"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Не удалось отправить сообщение ни одному получателю"))
                .andExpect(jsonPath("$.failed.length()").value(2));
    }

    @Test
    void testChannelUsesManualRecipientWithoutConfiguredTargets() throws Exception {
        Channel channel = new Channel();
        channel.setId(65L);
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setDeliverySettings("{}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        HttpClient httpClient = mock(HttpClient.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "ok": true
                }
                """);
        when(channelRepository.findById(65L)).thenReturn(Optional.of(channel));
        when(integrationNetworkService.createChannelHttpClient(any(Channel.class), any())).thenReturn(httpClient);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        mockMvc.perform(post("/api/channels/65/test-message")
                        .contentType("application/json")
                        .content("""
                                {
                                  "recipient": "manual-operator",
                                  "message": "manual ping"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sent.length()").value(1))
                .andExpect(jsonPath("$.sent[0]").value("manual-operator"))
                .andExpect(jsonPath("$.failed.length()").value(0));
    }

    @Test
    void refreshBotInfoReturnsNotFoundWhenChannelIsMissing() throws Exception {
        when(channelRepository.findById(71L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/channels/71/bot-info"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Канал не найден"));
    }

    @Test
    void refreshBotInfoRejectsNonTelegramPlatform() throws Exception {
        Channel channel = new Channel();
        channel.setId(72L);
        channel.setPlatform("max");
        channel.setToken("max-token");

        when(channelRepository.findById(72L)).thenReturn(Optional.of(channel));

        mockMvc.perform(post("/api/channels/72/bot-info"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Обновление доступно только для Telegram"));
    }

    @Test
    void refreshBotInfoReturnsBadRequestWhenTelegramTokenIsMissing() throws Exception {
        Channel channel = new Channel();
        channel.setId(721L);
        channel.setPlatform("telegram");
        channel.setToken("");

        when(channelRepository.findById(721L)).thenReturn(Optional.of(channel));

        mockMvc.perform(post("/api/channels/721/bot-info"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Не удалось получить данные бота"));

        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    void refreshBotInfoReturnsBadRequestWhenTelegramGetMeFails() throws Exception {
        Channel channel = new Channel();
        channel.setId(73L);
        channel.setPlatform("telegram");
        channel.setToken("tg-token");

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        HttpClient httpClient = mock(HttpClient.class);
        when(response.statusCode()).thenReturn(500);
        when(channelRepository.findById(73L)).thenReturn(Optional.of(channel));
        when(integrationNetworkService.createChannelHttpClient(any(Channel.class), any())).thenReturn(httpClient);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        mockMvc.perform(post("/api/channels/73/bot-info"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Не удалось получить данные бота"));

        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    void refreshBotInfoPersistsFetchedTelegramBotMetadata() throws Exception {
        Channel channel = new Channel();
        channel.setId(74L);
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setPublicId("public-74");
        channel.setQuestionsCfg("{}");
        channel.setDeliverySettings("{}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        HttpClient httpClient = mock(HttpClient.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "ok": true,
                  "result": {
                    "username": "support_bot",
                    "first_name": "Support",
                    "last_name": "Bot"
                  }
                }
                """);
        when(channelRepository.findById(74L)).thenReturn(Optional.of(channel));
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());
        when(integrationNetworkService.createChannelHttpClient(any(Channel.class), any())).thenReturn(httpClient);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        mockMvc.perform(post("/api/channels/74/bot-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.channel.bot_name").value("Support Bot"))
                .andExpect(jsonPath("$.channel.bot_username").value("support_bot"));

        verify(channelRepository).save(argThat(saved ->
                "Support Bot".equals(saved.getBotName())
                        && "support_bot".equals(saved.getBotUsername())
        ));
    }

    @Test
    void getChannelsEmbedsCredentialSummaryForBoundCredential() throws Exception {
        Channel channel = new Channel();
        channel.setId(12L);
        channel.setChannelName("Telegram Support");
        channel.setPlatform("telegram");
        channel.setCredentialId(5L);
        channel.setPublicId("pub-12");

        when(channelRepository.findAll()).thenReturn(List.of(channel));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(5L, "TG Main", "telegram", "123456:ABCDEF", true)
        ));

        mockMvc.perform(get("/api/channels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.channels[0].id").value(12))
            .andExpect(jsonPath("$.channels[0].credential_id").value(5))
            .andExpect(jsonPath("$.channels[0].credential.id").value(5))
            .andExpect(jsonPath("$.channels[0].credential.name").value("TG Main"))
            .andExpect(jsonPath("$.channels[0].credential.masked_token").exists());
    }

    @Test
    void getBotCredentialsReturnsSharedConfigCredentials() throws Exception {
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(5L, "TG Main", "telegram", "123456:ABCDEF", true)
        ));

        mockMvc.perform(get("/api/bot-credentials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.credentials[0].id").value(5))
            .andExpect(jsonPath("$.credentials[0].name").value("TG Main"))
            .andExpect(jsonPath("$.credentials[0].platform").value("telegram"))
            .andExpect(jsonPath("$.credentials[0].is_active").value(true))
            .andExpect(jsonPath("$.credentials[0].masked_token").exists());
    }

    @Test
    void createBotCredentialRejectsMissingNameOrToken() throws Exception {
        mockMvc.perform(post("/api/bot-credentials")
                .contentType("application/json")
                .content("""
                    {
                      "name": "VK Main",
                      "token": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Название и токен обязательны"));
    }

    @Test
    void createBotCredentialPersistsExpandedSharedConfigCredentialList() throws Exception {
        List<BotCredential> existing = new ArrayList<>();
        existing.add(new BotCredential(2L, "Legacy", "telegram", "legacy-token", true));
        when(sharedConfigService.loadBotCredentials()).thenReturn(existing);

        mockMvc.perform(post("/api/bot-credentials")
                .contentType("application/json")
                .content("""
                    {
                      "name": "VK Main",
                      "platform": "vk",
                      "token": "vk-token",
                      "is_active": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.credential.id").value(3))
            .andExpect(jsonPath("$.credential.name").value("VK Main"))
            .andExpect(jsonPath("$.credential.platform").value("vk"))
            .andExpect(jsonPath("$.credential.is_active").value(false));

        verify(sharedConfigService).saveBotCredentials(argThat(credentials ->
            credentials.size() == 2
                && credentials.stream().anyMatch(item -> item.id() == 2L && "Legacy".equals(item.name()))
                && credentials.stream().anyMatch(item -> item.id() == 3L && "VK Main".equals(item.name()) && "vk".equals(item.platform()) && Boolean.FALSE.equals(item.active()))
        ));
    }

    @Test
    void createBotCredentialNormalizesBlankPlatformToTelegram() throws Exception {
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());

        mockMvc.perform(post("/api/bot-credentials")
                .contentType("application/json")
                .content("""
                    {
                      "name": "Default TG",
                      "platform": "",
                      "token": "tg-token",
                      "is_active": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.credential.platform").value("telegram"));

        verify(sharedConfigService).saveBotCredentials(argThat(credentials ->
            credentials.size() == 1
                && "telegram".equals(credentials.get(0).platform())
        ));
    }

    @Test
    void deleteBotCredentialClearsLinkedChannelsAndPersistsTrimmedCredentialList() throws Exception {
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(7L, "TG Main", "telegram", "token-1", true),
            new BotCredential(8L, "VK Backup", "vk", "token-2", false)
        ));
        Channel linkedChannel = new Channel();
        linkedChannel.setId(101L);
        linkedChannel.setCredentialId(8L);
        when(channelRepository.findAll()).thenReturn(List.of(linkedChannel));

        mockMvc.perform(delete("/api/bot-credentials/8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(sharedConfigService).saveBotCredentials(argThat(credentials ->
            credentials.size() == 1 && credentials.get(0).id() == 7L
        ));
        verify(channelRepository).saveAll(argThat(channels ->
            channels.iterator().hasNext() && channels.iterator().next().getCredentialId() == null
        ));
    }

    @Test
    void deleteBotCredentialReturnsNotFoundWhenCredentialIsMissing() throws Exception {
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(7L, "TG Main", "telegram", "token-1", true)
        ));

        mockMvc.perform(delete("/api/bot-credentials/404"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Учётные данные не найдены"));
    }

    @Test
    void getChannelNotificationsReturnsEmptySuccessPayload() throws Exception {
        mockMvc.perform(get("/api/channel-notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.notifications").isArray())
            .andExpect(jsonPath("$.notifications.length()").value(0));
    }
}
