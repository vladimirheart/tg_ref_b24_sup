package com.example.panel.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.example.panel.service.ChannelTransportService;
import com.example.panel.service.IntegrationNetworkService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
    ChannelApiController.class,
    ChannelBotCredentialApiController.class,
    ChannelTelegramDiagnosticsApiController.class,
    ChannelNotificationApiController.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(ChannelTransportService.class)
class ChannelApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChannelRepository channelRepository;

    @Autowired
    private SharedConfigService sharedConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeIntegrationNetworkService integrationNetworkService;

    @BeforeEach
    void resetSharedConfigCredentials() {
        sharedConfigService.saveBotCredentials(List.of());
        integrationNetworkService.reset();
    }

    @TestConfiguration
    static class SharedConfigTestConfig {

        @Bean
        @Primary
        SharedConfigService sharedConfigService(ObjectMapper objectMapper) throws Exception {
            return new SharedConfigService(objectMapper, Files.createTempDirectory("channel-api-webmvc-shared").toString());
        }

        @Bean
        @Primary
        FakeIntegrationNetworkService integrationNetworkService(SharedConfigService sharedConfigService, ObjectMapper objectMapper) {
            return new FakeIntegrationNetworkService(sharedConfigService, objectMapper);
        }
    }

    static class FakeIntegrationNetworkService extends IntegrationNetworkService {

        private HttpClient httpClient;
        private RuntimeException createClientException;
        private String legacyTelegramBaseUrl;

        FakeIntegrationNetworkService(SharedConfigService sharedConfigService, ObjectMapper objectMapper) {
            super(sharedConfigService, objectMapper);
        }

        void reset() {
            httpClient = null;
            createClientException = null;
            legacyTelegramBaseUrl = null;
        }

        void setHttpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        void setCreateClientException(RuntimeException createClientException) {
            this.createClientException = createClientException;
        }

        void setLegacyTelegramBaseUrl(String legacyTelegramBaseUrl) {
            this.legacyTelegramBaseUrl = legacyTelegramBaseUrl;
        }

        @Override
        public HttpClient createChannelHttpClient(Channel channel, Duration timeout) {
            if (createClientException != null) {
                throw createClientException;
            }
            if (httpClient != null) {
                return httpClient;
            }
            return super.createChannelHttpClient(channel, timeout);
        }

        @Override
        public String resolveTelegramLegacyBotApiBaseUrl(Channel channel) {
            return legacyTelegramBaseUrl;
        }
    }

    static class StubHttpClient extends HttpClient {

        private final int statusCode;
        private final String responseBody;
        private HttpRequest lastRequest;

        StubHttpClient(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        String lastRequestUri() {
            return lastRequest != null ? lastRequest.uri().toString() : null;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.lastRequest = request;
            return (HttpResponse<T>) new StubHttpResponse(statusCode, responseBody, request);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    static class StubHttpResponse implements HttpResponse<String> {

        private final int statusCode;
        private final String body;
        private final HttpRequest request;

        StubHttpResponse(int statusCode, String body, HttpRequest request) {
            this.statusCode = statusCode;
            this.body = body;
            this.request = request;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Collections.emptyMap(), (name, value) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request != null ? request.uri() : URI.create("http://localhost");
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }

    @Test
    void regeneratePublicIdSupportsChannelsRouteAlias() throws Exception {
        Channel channel = new Channel();
        channel.setId(55L);
        channel.setPublicId("old_public_id");

        when(channelRepository.findById(55L)).thenReturn(Optional.of(channel));
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        sharedConfigService.saveBotCredentials(List.of());

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
    void getChannelsReturnsEmptySuccessPayloadWhenRepositoryIsEmpty() throws Exception {
        when(channelRepository.findAll()).thenReturn(List.of());
        sharedConfigService.saveBotCredentials(List.of());

        mockMvc.perform(get("/api/channels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.channels").isArray())
            .andExpect(jsonPath("$.channels.length()").value(0));
    }

    @Test
    void getChannelsContinuesWhenTelegramBotInfoRefreshFails() throws Exception {
        Channel channel = new Channel();
        channel.setId(11L);
        channel.setChannelName("TG Support");
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setPublicId("pub-11");

        when(channelRepository.findAll()).thenReturn(List.of(channel));
        sharedConfigService.saveBotCredentials(List.of());
        integrationNetworkService.setCreateClientException(new IllegalStateException("network offline"));

        mockMvc.perform(get("/api/channels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.channels[0].id").value(11))
            .andExpect(jsonPath("$.channels[0].channel_name").value("TG Support"));
    }

    @Test
    void getChannelsContinuesWhenSavingRefreshedTelegramBotInfoFails() throws Exception {
        Channel channel = new Channel();
        channel.setId(75L);
        channel.setChannelName("TG Support");
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setPlatformConfig("""
            {
              "base_url": "https://telegram.ftl-dev.ru/"
            }
            """);
        channel.setPublicId("pub-75");
        channel.setQuestionsCfg("{}");
        channel.setDeliverySettings("{}");

        StubHttpClient httpClient = new StubHttpClient(200, """
                {
                  "ok": true,
                  "result": {
                    "username": "support_bot",
                    "first_name": "Support"
                  }
                }
                """);
        when(channelRepository.findAll()).thenReturn(List.of(channel));
        when(channelRepository.saveAll(any())).thenThrow(new RuntimeException("db unavailable"));
        sharedConfigService.saveBotCredentials(List.of());
        integrationNetworkService.setHttpClient(httpClient);

        mockMvc.perform(get("/api/channels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.channels[0].bot_name").value("Support"))
            .andExpect(jsonPath("$.channels[0].bot_username").value("support_bot"));

        assertEquals("https://telegram.ftl-dev.ru/bottg-token/getMe", httpClient.lastRequestUri());
    }

    @Test
    void createChannelPersistsVkPlatformConfigAndTemplateSelections() throws Exception {
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            channel.setId(42L);
            return channel;
        });
        sharedConfigService.saveBotCredentials(List.of());

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
    void createChannelRejectsInvalidTelegramBaseUrl() throws Exception {
        mockMvc.perform(post("/api/channels")
                        .contentType("application/json")
                        .content("""
                                {
                                  "channel_name": "TG Bad Base URL",
                                  "platform": "telegram",
                                  "token": "telegram-token",
                                  "platform_config": {
                                    "base_url": "ftp://telegram.ftl-dev.ru"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Telegram Bot API base URL должен начинаться с http:// или https://."));
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
        sharedConfigService.saveBotCredentials(List.of());

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
        sharedConfigService.saveBotCredentials(List.of(
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
    void patchChannelRejectsVkPlatformSwitchWithoutCallbackConfiguration() throws Exception {
        Channel channel = new Channel();
        channel.setId(46L);
        channel.setChannelName("Platform Switch");
        channel.setPlatform("telegram");
        channel.setToken("vk-token");
        channel.setDeliverySettings("{}");
        channel.setQuestionsCfg("{}");
        channel.setPublicId("public-46");

        when(channelRepository.findById(46L)).thenReturn(Optional.of(channel));

        mockMvc.perform(post("/api/channels/46")
                        .contentType("application/json")
                        .content("""
                                {
                                  "platform": "vk"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Для VK укажите корректный ID сообщества (group_id)."));
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
        sharedConfigService.saveBotCredentials(List.of());

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

        StubHttpClient httpClient = new StubHttpClient(200, """
                {
                  "ok": true
                }
                """);
        when(channelRepository.findById(63L)).thenReturn(Optional.of(channel));
        integrationNetworkService.setHttpClient(httpClient);

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

        StubHttpClient httpClient = new StubHttpClient(500, "");
        when(channelRepository.findById(64L)).thenReturn(Optional.of(channel));
        integrationNetworkService.setHttpClient(httpClient);

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

        StubHttpClient httpClient = new StubHttpClient(200, """
                {
                  "ok": true
                }
                """);
        when(channelRepository.findById(65L)).thenReturn(Optional.of(channel));
        integrationNetworkService.setHttpClient(httpClient);

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

        StubHttpClient httpClient = new StubHttpClient(500, "");
        when(channelRepository.findById(73L)).thenReturn(Optional.of(channel));
        integrationNetworkService.setHttpClient(httpClient);

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

        StubHttpClient httpClient = new StubHttpClient(200, """
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
        sharedConfigService.saveBotCredentials(List.of());
        integrationNetworkService.setHttpClient(httpClient);

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
    void refreshBotInfoUsesLegacyTelegramMirrorBaseUrlWhenPlatformConfigIsEmpty() throws Exception {
        Channel channel = new Channel();
        channel.setId(77L);
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setPublicId("public-77");
        channel.setQuestionsCfg("{}");
        channel.setDeliverySettings("""
            {
              "network_route": {
                "mode": "proxy",
                "proxy": {
                  "scheme": "https",
                  "host": "telegram.ftl-dev.ru",
                  "port": 443
                }
              }
            }
            """);

        StubHttpClient httpClient = new StubHttpClient(200, """
                {
                  "ok": true,
                  "result": {
                    "username": "support_bot",
                    "first_name": "Support"
                  }
                }
                """);
        when(channelRepository.findById(77L)).thenReturn(Optional.of(channel));
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        sharedConfigService.saveBotCredentials(List.of());
        integrationNetworkService.setLegacyTelegramBaseUrl("https://telegram.ftl-dev.ru");
        integrationNetworkService.setHttpClient(httpClient);

        mockMvc.perform(post("/api/channels/77/bot-info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.channel.bot_username").value("support_bot"));

        assertEquals("https://telegram.ftl-dev.ru/bottg-token/getMe", httpClient.lastRequestUri());
    }

    @Test
    void refreshBotInfoReturnsBadRequestWhenTelegramResultIsEmpty() throws Exception {
        Channel channel = new Channel();
        channel.setId(76L);
        channel.setPlatform("telegram");
        channel.setToken("tg-token");
        channel.setPublicId("public-76");
        channel.setQuestionsCfg("{}");
        channel.setDeliverySettings("{}");

        StubHttpClient httpClient = new StubHttpClient(200, """
                {
                  "ok": true,
                  "result": {}
                }
                """);
        when(channelRepository.findById(76L)).thenReturn(Optional.of(channel));
        integrationNetworkService.setHttpClient(httpClient);

        mockMvc.perform(post("/api/channels/76/bot-info"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Не удалось получить данные бота"));

        verify(channelRepository, never()).save(any(Channel.class));
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
        sharedConfigService.saveBotCredentials(List.of(
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
        sharedConfigService.saveBotCredentials(List.of(
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
    void getBotCredentialsNormalizesBlankPlatformToTelegram() throws Exception {
        sharedConfigService.saveBotCredentials(List.of(
            new BotCredential(6L, "Fallback TG", "", "123456:ABCDEF", true)
        ));

        mockMvc.perform(get("/api/bot-credentials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.credentials[0].platform").value("telegram"));
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
        sharedConfigService.saveBotCredentials(existing);

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

        List<BotCredential> savedCredentials = sharedConfigService.loadBotCredentials();
        assertEquals(2, savedCredentials.size());
        assertTrue(savedCredentials.stream().anyMatch(item -> item.id() == 2L && "Legacy".equals(item.name())));
        assertTrue(savedCredentials.stream().anyMatch(item -> item.id() == 3L && "VK Main".equals(item.name()) && "vk".equals(item.platform()) && Boolean.FALSE.equals(item.active())));
    }

    @Test
    void createBotCredentialNormalizesBlankPlatformToTelegram() throws Exception {
        sharedConfigService.saveBotCredentials(List.of());

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

        List<BotCredential> savedCredentials = sharedConfigService.loadBotCredentials();
        assertEquals(1, savedCredentials.size());
        assertEquals("telegram", savedCredentials.get(0).platform());
    }

    @Test
    void createBotCredentialDefaultsIsActiveToTrueWhenFlagIsMissing() throws Exception {
        sharedConfigService.saveBotCredentials(List.of());

        mockMvc.perform(post("/api/bot-credentials")
                .contentType("application/json")
                .content("""
                    {
                      "name": "TG Default",
                      "token": "tg-token"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.credential.is_active").value(true));

        List<BotCredential> savedCredentials = sharedConfigService.loadBotCredentials();
        assertEquals(1, savedCredentials.size());
        assertTrue(Boolean.TRUE.equals(savedCredentials.get(0).active()));
    }

    @Test
    void createBotCredentialAssignsNextIdAfterSparseAndNullExistingIds() throws Exception {
        sharedConfigService.saveBotCredentials(List.of(
            new BotCredential(null, "Legacy Null", "telegram", "token-0", true),
            new BotCredential(10L, "TG Main", "telegram", "token-1", true),
            new BotCredential(4L, "VK Backup", "vk", "token-2", false)
        ));

        mockMvc.perform(post("/api/bot-credentials")
                .contentType("application/json")
                .content("""
                    {
                      "name": "MAX Main",
                      "platform": "max",
                      "token": "max-token",
                      "is_active": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.credential.id").value(11))
            .andExpect(jsonPath("$.credential.platform").value("max"));
    }

    @Test
    void deleteBotCredentialClearsLinkedChannelsAndPersistsTrimmedCredentialList() throws Exception {
        sharedConfigService.saveBotCredentials(List.of(
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

        List<BotCredential> savedCredentials = sharedConfigService.loadBotCredentials();
        assertEquals(1, savedCredentials.size());
        assertEquals(7L, savedCredentials.get(0).id());
        verify(channelRepository).saveAll(argThat(channels ->
            channels.iterator().hasNext() && channels.iterator().next().getCredentialId() == null
        ));
    }

    @Test
    void deleteBotCredentialReturnsNotFoundWhenCredentialIsMissing() throws Exception {
        sharedConfigService.saveBotCredentials(List.of(
            new BotCredential(7L, "TG Main", "telegram", "token-1", true)
        ));

        mockMvc.perform(delete("/api/bot-credentials/404"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Учётные данные не найдены"));
    }

    @Test
    void deleteBotCredentialSkipsChannelSaveWhenNoChannelsReferenceCredential() throws Exception {
        sharedConfigService.saveBotCredentials(List.of(
            new BotCredential(7L, "TG Main", "telegram", "token-1", true)
        ));
        Channel unrelatedChannel = new Channel();
        unrelatedChannel.setId(102L);
        unrelatedChannel.setCredentialId(99L);
        when(channelRepository.findAll()).thenReturn(List.of(unrelatedChannel));

        mockMvc.perform(delete("/api/bot-credentials/7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(channelRepository, never()).saveAll(any());
    }

    @Test
    void deleteBotCredentialClearsMultipleLinkedChannels() throws Exception {
        sharedConfigService.saveBotCredentials(List.of(
            new BotCredential(7L, "TG Main", "telegram", "token-1", true),
            new BotCredential(8L, "VK Backup", "vk", "token-2", false)
        ));
        Channel first = new Channel();
        first.setId(201L);
        first.setCredentialId(8L);
        Channel second = new Channel();
        second.setId(202L);
        second.setCredentialId(8L);
        when(channelRepository.findAll()).thenReturn(List.of(first, second));

        mockMvc.perform(delete("/api/bot-credentials/8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(channelRepository).saveAll(argThat(channels -> {
            List<Channel> items = new ArrayList<>();
            channels.forEach(items::add);
            return items.size() == 2 && items.stream().allMatch(item -> item.getCredentialId() == null);
        }));
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
