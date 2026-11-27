package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class DialogReplyTransportServiceTest {
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"init,413","binary,413","init,429","binary,429","init,200","binary,200"})
    void maxUploadPreservesActualRejectionAtEitherStage(String stage,int status) throws Exception {
        HttpClient http=mock(HttpClient.class);var network=mock(IntegrationNetworkService.class);
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenReturn(http);
        List<HttpRequest> requests=new ArrayList<>();
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{
            HttpRequest request=call.getArgument(0);requests.add(request);
            if(stage.equals("binary")&&request.uri().getPath().equals("/uploads"))
                return responseFor(request,new ArrayList<>(),"{\"url\":\"https://upload.okcdn.ru/file\"}",200);
            return responseFor(request,new ArrayList<>(),"{\"code\":\"upload.rejected\",\"message\":\"Provider upload limit is 73 MiB\"}",status);
        });
        var transport=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        var result=transport.sendMedia(maxChannel(),42L,new MockMultipartFile("file","file.bin","application/octet-stream",new byte[]{1}),"",null,null);
        assertThat(result.success()).isFalse();assertThat(result.error()).contains("73 MiB","upload.rejected").doesNotContain("50 МБ","25 МБ");
        assertThat(result.httpStatus()).isEqualTo(status);assertThat(requests).hasSize(stage.equals("init")?1:2);
        assertThat(requests).noneMatch(r->r.uri().getPath().equals("/messages"));
    }

    @ParameterizedTest @ValueSource(strings={"init","binary"})
    void uncertainMaxUploadStopsWithoutSendingOrTryingAnotherHost(String stage) throws Exception {
        HttpClient http=mock(HttpClient.class);var network=mock(IntegrationNetworkService.class);
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenReturn(http);
        List<HttpRequest> requests=new ArrayList<>();
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{
            HttpRequest request=call.getArgument(0);requests.add(request);
            if(stage.equals("binary")&&request.uri().getPath().equals("/uploads"))
                return responseFor(request,new ArrayList<>(),"{\"url\":\"https://upload.okcdn.ru/file\"}",200);
            throw new IOException("Authorization: Bearer private");
        });
        var transport=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        var result=transport.sendMedia(maxChannel(),42L,new MockMultipartFile("file","file.bin","application/octet-stream",new byte[]{1}),"",null,null);
        assertThat(result.error()).contains("не подтвержден").doesNotContain("private");
        assertThat(requests).hasSize(stage.equals("init")?1:2);
    }

    @Test void successfulMaxUploadStillSendsOneMessageAndLongTelegramCaptionReachesApi() throws Exception {
        HttpClient http=mock(HttpClient.class);var network=mock(IntegrationNetworkService.class);
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenReturn(http);
        List<HttpRequest> requests=new ArrayList<>();
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{
            HttpRequest request=call.getArgument(0);requests.add(request);
            String body=switch(request.uri().getPath()){
                case "/uploads"->"{\"url\":\"https://upload.okcdn.ru/file\"}";
                case "/file"->"{\"token\":\"fixture-upload-token\"}";
                case "/messages"->"{\"message\":{\"body\":{\"mid\":\"77\"}}}";
                default->"{\"ok\":false,\"description\":\"Bad Request: caption is too long\"}";
            };
            return responseFor(request,new ArrayList<>(),body,request.uri().getPath().endsWith("/sendDocument")?400:200);
        });
        var transport=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        var file=new MockMultipartFile("file","file.bin","application/octet-stream",new byte[]{1});
        assertThat(transport.sendMedia(maxChannel(),42L,file,"",null,null).success()).isTrue();
        assertThat(requests).hasSize(3);
        var result=transport.sendMedia(telegramChannel(),42L,file,"x".repeat(1100),null,null);
        assertThat(result.error()).contains("caption is too long").doesNotContain("1024");
        assertThat(requests).hasSize(4);
    }
    @ParameterizedTest @ValueSource(ints={502,503,504})
    void ambiguousLegacyGatewayDoesNotSilentlyResendTelegramPost(int status) throws Exception {
        HttpClient http=mock(HttpClient.class), direct=mock(HttpClient.class);
        var network=mock(IntegrationNetworkService.class);
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenReturn(http);
        when(network.createDirectHttpClient(any(Duration.class))).thenReturn(direct);
        when(network.resolveTelegramLegacyBotApiBaseUrl(any())).thenReturn("https://telegram.ftl-dev.ru");
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->
            responseFor(call.getArgument(0),new ArrayList<>(),"<html>gateway error</html>",status));
        when(direct.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->
            responseFor(call.getArgument(0),new ArrayList<>(),"{\"ok\":true,\"result\":{\"message_id\":99}}",200));
        Channel channel=new Channel();channel.setPlatform("telegram");channel.setToken("fixture-token");
        var service=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        var result=service.sendTextGuarded(channel,42L,"Проверка",null,()->true);
        assertThat(result.success()).isFalse();assertThat(result.error()).contains("не подтвержден");
        verify(http,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        verify(direct,never()).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @ParameterizedTest @ValueSource(strings={"gateway","malformed","network"})
    void uncertainMaxMediaPostDoesNotFallThroughToAnotherHost(String kind) throws Exception {
        HttpClient http=mock(HttpClient.class);var network=mock(IntegrationNetworkService.class);
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenReturn(http);
        if(kind.equals("network"))when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("private transport details"));
        else when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->
            responseFor(call.getArgument(0),new ArrayList<>(),"<html>gateway error</html>",kind.equals("gateway")?502:200));
        Channel channel=new Channel();channel.setPlatform("max");channel.setToken("fixture-token");
        var service=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        var result=(DialogReplyTransportService.DialogReplyTransportResult)org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,"sendMaxMediaMessage",channel,"fixture-token",42L,java.util.Map.of("text","Проверка"),1L);
        assertThat(result.success()).isFalse();assertThat(result.error()).containsAnyOf("не подтвержден","подтвердить");
        verify(http,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void malformedSuccessfulHttpResponseNeverAcknowledgesMessage(String platform) throws Exception {
        HttpClient http = mock(HttpClient.class);
        var network = mock(IntegrationNetworkService.class);
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenReturn(http);
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call ->
            responseFor(call.getArgument(0),new ArrayList<>(),"<html>gateway error</html>",200));
        Channel channel = new Channel();channel.setPlatform(platform);channel.setToken("fixture-token");
        var service = new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        var result = service.sendTextGuarded(channel,42L,"Проверка",null,()->true);
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("подтвердить").doesNotContain("<html>");
        assertThat(result.telegramMessageId()).isNull();
    }

    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void guardedTextUsesFrozenRequestChannelDespiteMutableNetworkSetup(String platform) throws Exception {
        HttpClient http=mock(HttpClient.class);
        IntegrationNetworkService network=mock(IntegrationNetworkService.class);
        Channel channel=new Channel();channel.setPlatform(platform);channel.setToken("frozen-test-token");
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenAnswer(call->{
            Channel copy=call.getArgument(0);assertThat(copy).isNotSameAs(channel);
            copy.setToken("tampered");copy.setPlatformConfig("{\"telegram_bot_api_base_url\":\"https://example.invalid\"}");
            return http;
        });
        List<HttpRequest> requests=new ArrayList<>();
        String response=switch(platform) {
            case "vk"->"{\"response\":77}";
            case "max"->"{\"message\":{\"body\":{\"mid\":\"77\"}}}";
            default->"{\"ok\":true,\"result\":{\"message_id\":77}}";
        };
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{
            HttpRequest request=call.getArgument(0);requests.add(request);
            return responseFor(request,new ArrayList<>(),response,200);
        });
        var service=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        assertThat(service.sendTextGuarded(channel,42L,"Ответ",null,()->true).success()).isTrue();
        assertThat(requests).hasSize(1);HttpRequest request=requests.get(0);
        assertThat(request.uri().toString()).doesNotContain("tampered","example.invalid");
        assertThat(requestBody(request)).doesNotContain("tampered");
        if ("telegram".equals(platform)) assertThat(request.uri().toString()).contains("botfrozen-test-token");
        if ("vk".equals(platform)) assertThat(requestBody(request)).contains("access_token=frozen-test-token");
        if ("max".equals(platform)) assertThat(request.headers().firstValue("Authorization")).contains("frozen-test-token");
    }

    @Test
    void retriesTelegramTextDirectlyOnlyAfterExplicitLegacyMirrorRejection() throws Exception {
        HttpClient mirrorClient = mock(HttpClient.class);
        HttpClient directClient = mock(HttpClient.class);
        IntegrationNetworkService network = mock(IntegrationNetworkService.class);
        Channel channel = new Channel();
        channel.setId(7L);
        channel.setPlatform("telegram");
        channel.setToken("test-token");
        when(network.createChannelHttpClient(any(), any(Duration.class))).thenReturn(mirrorClient);
        when(network.resolveTelegramLegacyBotApiBaseUrl(channel)).thenReturn("https://telegram.ftl-dev.ru");
        when(network.createDirectHttpClient(Duration.ofSeconds(15))).thenReturn(directClient);
        when(mirrorClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(call -> responseFor(call.getArgument(0), new ArrayList<>(), "{\"ok\":false,\"error_code\":502,\"description\":\"Request rejected\"}", 502));
        List<HttpRequest> directRequests = new ArrayList<>();
        when(directClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(call -> {
                HttpRequest request = call.getArgument(0);
                directRequests.add(request);
                return responseFor(request, new ArrayList<>(), "{\"ok\":true,\"result\":{\"message_id\":93}}", 200);
            });

        DialogReplyTransportService service = new DialogReplyTransportService(
            mock(com.example.panel.repository.ChannelRepository.class), network, new ObjectMapper());

        DialogReplyTransportService.DialogReplyTransportResult result = service.sendText(channel, 42L, "Ответ", null);

        assertThat(result.success()).isTrue();
        assertThat(result.telegramMessageId()).isEqualTo(93L);
        assertThat(directRequests).singleElement().satisfies(request ->
            assertThat(request.uri().toString()).isEqualTo("https://api.telegram.org/bottest-token/sendMessage"));
    }

    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void absentSendGuardNeverCallsProvider(String platform) throws Exception {
        HttpClient http=mock(HttpClient.class);IntegrationNetworkService network=mock(IntegrationNetworkService.class);
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenReturn(http);
        Channel channel=new Channel();channel.setPlatform(platform);channel.setToken("test-token");
        var service=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        assertThat(service.sendTextGuarded(channel,42L,"Ответ",null,null).classification()).isEqualTo("scope_denied");
        verify(http,never()).send(any(),any(HttpResponse.BodyHandler.class));
    }

    @ParameterizedTest @ValueSource(strings={"unknown","","TELEGRAM"})
    void guardedTextDoesNotTurnUnknownPlatformIntoTelegram(String platform) {
        IntegrationNetworkService network=mock(IntegrationNetworkService.class);
        Channel channel=new Channel();channel.setPlatform(platform);channel.setToken("test-token");
        var service=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        assertThat(service.sendTextGuarded(channel,42L,"Ответ",null,()->true).classification()).isEqualTo("scope_denied");
        verify(network,never()).createChannelHttpClient(any(),any(Duration.class));
    }

    @Test
    void sendMaxTextIncludesReplyLink() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
        when(integrationNetworkService.createChannelHttpClient(any(), any(Duration.class))).thenReturn(httpClient);

        List<HttpRequest> requests = new ArrayList<>();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    return responseFor(request, new ArrayList<>(),
                            "{\"message\":{\"body\":{\"mid\":\"9002\"}}}", 200);
                });

        DialogReplyTransportService service = new DialogReplyTransportService(
                mock(com.example.panel.repository.ChannelRepository.class),
                integrationNetworkService,
                new ObjectMapper()
        );

        DialogReplyTransportService.DialogReplyTransportResult result = service.sendText(
                maxChannel(), 42L, "Ответ оператора", 9001L);

        assertThat(result.success()).isTrue();
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).uri().getHost()).isEqualTo("platform-api2.max.ru");
        var link = new ObjectMapper().readTree(requestBody(requests.get(0))).path("link");
        assertThat(link.path("type").asText()).isEqualTo("reply");
        assertThat(link.path("mid").asText()).isEqualTo("9001");
    }

    @Test
    void sendMediaSendsJpgAsTelegramDocument() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
        when(integrationNetworkService.createChannelHttpClient(any(), any(Duration.class))).thenReturn(httpClient);

        List<String> methodCalls = new ArrayList<>();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> responseFor(invocation.getArgument(0), methodCalls,
                        "{\"ok\":true,\"result\":{\"message_id\":77}}",
                        200));

        DialogReplyTransportService service = new DialogReplyTransportService(
                mock(com.example.panel.repository.ChannelRepository.class),
                integrationNetworkService,
                new ObjectMapper()
        );

        DialogReplyTransportService.DialogReplyTransportResult result = service.sendMedia(
                telegramChannel(),
                42L,
                new MockMultipartFile("file", "sample.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                "caption",
                "sample.jpg",
                null
        );

        assertThat(result.error()).isNull();
        assertThat(result.telegramMessageId()).isEqualTo(77L);
        assertThat(methodCalls).containsExactly("sendDocument");
    }

    @Test
    void sendMediaSendsMp4AsTelegramDocument() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
        when(integrationNetworkService.createChannelHttpClient(any(), any(Duration.class))).thenReturn(httpClient);

        List<String> methodCalls = new ArrayList<>();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> responseFor(invocation.getArgument(0), methodCalls,
                        "{\"ok\":true,\"result\":{\"message_id\":91}}",
                        200));

        DialogReplyTransportService service = new DialogReplyTransportService(
                mock(com.example.panel.repository.ChannelRepository.class),
                integrationNetworkService,
                new ObjectMapper()
        );

        DialogReplyTransportService.DialogReplyTransportResult result = service.sendMedia(
                telegramChannel(),
                42L,
                new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[]{4, 5, 6}),
                "",
                "sample.mp4",
                null
        );

        assertThat(result.error()).isNull();
        assertThat(result.telegramMessageId()).isEqualTo(91L);
        assertThat(methodCalls).containsExactly("sendDocument");
        verify(integrationNetworkService).createChannelHttpClient(any(), eq(Duration.ofSeconds(120)));
    }

    @Test
    void sendMediaAllowsEightMegabytesMp4Preflight() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
        when(integrationNetworkService.createChannelHttpClient(any(), any(Duration.class))).thenReturn(httpClient);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> responseFor(invocation.getArgument(0), new ArrayList<>(),
                        "{\"ok\":true,\"result\":{\"message_id\":108}}",
                        200));

        DialogReplyTransportService service = new DialogReplyTransportService(
                mock(com.example.panel.repository.ChannelRepository.class),
                integrationNetworkService,
                new ObjectMapper()
        );

        byte[] bytes = new byte[8 * 1024 * 1024];
        DialogReplyTransportService.DialogReplyTransportResult result = service.sendMedia(
                telegramChannel(),
                42L,
                new MockMultipartFile("file", "clip.mp4", "video/mp4", bytes),
                "video",
                "clip.mp4",
                null
        );

        assertThat(result.error()).isNull();
        assertThat(result.telegramMessageId()).isEqualTo(108L);
    }

    @Test
    void sendMediaPassesFileAboveFormerLimitToApiAndUsesItsActualResponse() throws Exception {
        HttpClient http = mock(HttpClient.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
        when(integrationNetworkService.createChannelHttpClient(any(), any(Duration.class))).thenReturn(http);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(call -> responseFor(call.getArgument(0),new ArrayList<>(),
                "{\"ok\":false,\"error_code\":413,\"description\":\"Request rejected by upstream proxy: upload exceeds 73 MiB\"}",413));
        DialogReplyTransportService service = new DialogReplyTransportService(
                mock(com.example.panel.repository.ChannelRepository.class),
                integrationNetworkService,
                new ObjectMapper()
        );

        MultipartFile oversizedFile = new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return "oversized.mp4";
            }

            @Override
            public String getContentType() {
                return "video/mp4";
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return 50L * 1024L * 1024L + 1L;
            }

            @Override
            public byte[] getBytes() {
                return new byte[0];
            }

            @Override
            public InputStream getInputStream() {
                return InputStream.nullInputStream();
            }

            @Override
            public void transferTo(java.io.File dest) {
                throw new UnsupportedOperationException();
            }
        };

        DialogReplyTransportService.DialogReplyTransportResult result = service.sendMedia(
                telegramChannel(),
                42L,
                oversizedFile,
                "video",
                "oversized.mp4",
                null
        );

        assertThat(result.error()).contains("73 MiB", "upstream proxy", "413").doesNotContain("50 МБ");
        assertThat(result.httpStatus()).isEqualTo(413);
        assertThat(result.telegramMessageId()).isNull();
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void sendMediaReturnsReadableTimeoutError() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
        when(integrationNetworkService.createChannelHttpClient(any(), any(Duration.class))).thenReturn(httpClient);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("timeout"));

        DialogReplyTransportService service = new DialogReplyTransportService(
                mock(com.example.panel.repository.ChannelRepository.class),
                integrationNetworkService,
                new ObjectMapper()
        );

        DialogReplyTransportService.DialogReplyTransportResult result = service.sendMedia(
                telegramChannel(),
                42L,
                new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{1, 2, 3}),
                "video",
                "clip.mp4",
                null
        );

        assertThat(result.error()).startsWith("Не удалось отправить файл в Telegram: превышено время ожидания загрузки.").contains("Результат операции не подтвержден", "Проверьте историю");
        assertThat(result.telegramMessageId()).isNull();
    }

    @Test
    void sendMediaReturnsTelegramErrorDescriptionWithoutMojibake() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
        when(integrationNetworkService.createChannelHttpClient(any(), any(Duration.class))).thenReturn(httpClient);

        List<String> methodCalls = new ArrayList<>();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> responseFor(invocation.getArgument(0), methodCalls,
                        "{\"ok\":false,\"description\":\"Bad Request: wrong file identifier/HTTP URL specified\"}",
                        400));

        DialogReplyTransportService service = new DialogReplyTransportService(
                mock(com.example.panel.repository.ChannelRepository.class),
                integrationNetworkService,
                new ObjectMapper()
        );

        DialogReplyTransportService.DialogReplyTransportResult result = service.sendMedia(
                telegramChannel(),
                42L,
                new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[]{4, 5, 6}),
                "",
                "sample.mp4",
                null
        );

        assertThat(result.error()).startsWith("Telegram:").contains("wrong file identifier/HTTP URL specified");
        assertThat(result.error()).doesNotContain("Рќ", "Рµ", "Рѕ");
        assertThat(result.telegramMessageId()).isNull();
        assertThat(methodCalls).containsExactly("sendDocument");
    }

    @Test
    void sendMediaMapsTooLargeTelegramDescriptionToReadableSizeError() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
        when(integrationNetworkService.createChannelHttpClient(any(), any(Duration.class))).thenReturn(httpClient);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> responseFor(invocation.getArgument(0), new ArrayList<>(),
                        "{\"ok\":false,\"description\":\"Bad Request: file is too big\"}",
                        400));

        DialogReplyTransportService service = new DialogReplyTransportService(
                mock(com.example.panel.repository.ChannelRepository.class),
                integrationNetworkService,
                new ObjectMapper()
        );

        DialogReplyTransportService.DialogReplyTransportResult result = service.sendMedia(
                telegramChannel(),
                42L,
                new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[]{4, 5, 6}),
                "",
                "sample.mp4",
                null
        );

        assertThat(result.error()).contains("Telegram:", "Превышен допустимый размер", "file is too big").doesNotContain("50 МБ");
        assertThat(result.telegramMessageId()).isNull();
    }

    @Test
    void sendMediaReturnsGracefulErrorWhenMultipartBuildFails() {
        HttpClient httpClient = mock(HttpClient.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
        when(integrationNetworkService.createChannelHttpClient(any(), any(Duration.class))).thenReturn(httpClient);

        DialogReplyTransportService service = new DialogReplyTransportService(
                mock(com.example.panel.repository.ChannelRepository.class),
                integrationNetworkService,
                new ObjectMapper()
        );

        MultipartFile brokenFile = new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return "broken.jpg";
            }

            @Override
            public String getContentType() {
                return "image/jpeg";
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return 1L;
            }

            @Override
            public byte[] getBytes() {
                return new byte[]{1};
            }

            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("boom");
            }

            @Override
            public void transferTo(java.io.File dest) {
                throw new UnsupportedOperationException();
            }
        };

        DialogReplyTransportService.DialogReplyTransportResult result = service.sendMedia(
                telegramChannel(),
                42L,
                brokenFile,
                "",
                "broken.jpg",
                null
        );

        assertThat(result.error()).contains("Не удалось отправить файл в Telegram", "Результат операции не подтвержден", "Проверьте историю");
        assertThat(result.error()).doesNotContain("Рќ", "Рµ", "Рѕ");
        assertThat(result.telegramMessageId()).isNull();
    }

    @Test
    void sourceDoesNotContainReadAllBytesOrMojibakeMarkers() throws Exception {
        Path source = Path.of("src/main/java/com/example/panel/service/DialogReplyTransportService.java");
        String content = Files.readString(source, StandardCharsets.UTF_8);

        assertThat(content).doesNotContain("readAllBytes(");
        assertThat(content).doesNotContain("РќРµ");
        assertThat(content).doesNotContain("Р¤Р°Р№Р»");
        assertThat(content).doesNotContain("вЂ");
    }

    private static HttpResponse<String> responseFor(HttpRequest request,
                                                    List<String> methodCalls,
                                                    String body,
                                                    int statusCode) {
        URI uri = request.uri();
        String path = uri.getPath();
        methodCalls.add(path.substring(path.lastIndexOf('/') + 1));
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static String requestBody(HttpRequest request) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                ByteBuffer copy = item.slice();
                byte[] chunk = new byte[copy.remaining()];
                copy.get(chunk);
                bytes.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                completed.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                completed.complete(null);
            }
        });
        completed.get();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static Channel telegramChannel() {
        Channel channel = new Channel();
        channel.setPlatform("telegram");
        channel.setToken("123456:test-token");
        return channel;
    }

    private static Channel maxChannel() {
        Channel channel = new Channel();
        channel.setPlatform("max");
        channel.setToken("max-test-token");
        return channel;
    }

    @ParameterizedTest @ValueSource(strings={"EDIT","DELETE"})
    void guardedTelegramChangeUsesFrozenChannelAndBody(String action) throws Exception {
        HttpClient http=mock(HttpClient.class);IntegrationNetworkService network=mock(IntegrationNetworkService.class);
        Channel channel=new Channel();channel.setPlatform("telegram");channel.setToken("frozen-change-token");
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenAnswer(call->{
            Channel copy=call.getArgument(0);assertThat(copy).isNotSameAs(channel);copy.setToken("tampered");
            copy.setPlatformConfig("{\"telegram_bot_api_base_url\":\"https://example.invalid\"}");return http;
        });
        List<HttpRequest> requests=new ArrayList<>();
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{
            HttpRequest request=call.getArgument(0);requests.add(request);
            return responseFor(request,new ArrayList<>(),"{\"ok\":true,\"result\":{\"message_id\":77}}",200);
        });
        var service=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        var result="EDIT".equals(action)?service.editTelegramMessageGuarded(channel,42L,77L,"Исправлено 😀",()->true)
            :service.deleteTelegramMessageGuarded(channel,42L,77L,()->true);
        assertThat(result.success()).isTrue();assertThat(requests).hasSize(1);
        HttpRequest request=requests.get(0);assertThat(request.uri().toString()).contains("botfrozen-change-token").doesNotContain("tampered","example.invalid");
        var body=new ObjectMapper().readTree(requestBody(request));assertThat(body.path("chat_id").asLong()).isEqualTo(42L);
        assertThat(body.path("message_id").asLong()).isEqualTo(77L);
        if("EDIT".equals(action))assertThat(body.path("text").asText()).isEqualTo("Исправлено 😀");else assertThat(body.has("text")).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"EDIT","DELETE"})
    void absentOrDeniedTelegramChangeGuardNeverCallsProvider(String action) throws Exception {
        HttpClient http=mock(HttpClient.class);IntegrationNetworkService network=mock(IntegrationNetworkService.class);
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenReturn(http);
        Channel channel=new Channel();channel.setPlatform("telegram");channel.setToken("test-token");
        var service=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        for(java.util.function.BooleanSupplier guard:new java.util.function.BooleanSupplier[]{null,()->false}) {
            var result="EDIT".equals(action)?service.editTelegramMessageGuarded(channel,42L,77L,"Changed",guard)
                :service.deleteTelegramMessageGuarded(channel,42L,77L,guard);
            assertThat(result.classification()).isEqualTo("scope_denied");
        }
        channel.setPlatform("max");
        assertThat(service.editTelegramMessageGuarded(channel,42L,77L,"Changed",()->true).classification()).isEqualTo("scope_denied");
        assertThat(service.deleteTelegramMessageGuarded(channel,42L,77L,()->true).classification()).isEqualTo("scope_denied");
        verify(http,never()).send(any(),any(HttpResponse.BodyHandler.class));
    }

    @Test void guardedDocumentUsesFrozenChannelAndExactMultipartBody()throws Exception{
        HttpClient http=mock(HttpClient.class);IntegrationNetworkService network=mock(IntegrationNetworkService.class);Channel channel=telegramChannel();
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenAnswer(call->{Channel copy=call.getArgument(0);assertThat(copy).isNotSameAs(channel);copy.setToken("tampered");return http;});
        List<String> bodies=new ArrayList<>();
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{HttpRequest request=call.getArgument(0);
            assertThat(request.uri().toString()).endsWith("/sendDocument").doesNotContain("tampered");bodies.add(requestBody(request));
            return responseFor(request,new ArrayList<>(),"{\"ok\":true,\"result\":{\"message_id\":77}}",200);
        });
        var transport=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        byte[] bytes="DOC\u0000BYTES\n".getBytes(StandardCharsets.UTF_8);
        assertThat(transport.sendTelegramDocumentGuarded(channel,42L,new MockMultipartFile("file","report.pdf","application/octet-stream",bytes)," caption ",()->true).success()).isTrue();
        assertThat(bodies).singleElement().satisfies(body->assertThat(body).contains("name=\"chat_id\"","42","name=\"document\"","report.pdf","application/octet-stream","DOC\u0000BYTES\n","caption"));
    }
    @ParameterizedTest @ValueSource(strings={"false","null","unsupported"})
    void deniedDocumentGuardNeverCallsProvider(String mode)throws Exception{
        HttpClient http=mock(HttpClient.class);IntegrationNetworkService network=mock(IntegrationNetworkService.class);
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenReturn(http);Channel channel=telegramChannel();if(mode.equals("unsupported"))channel.setPlatform("max");
        var transport=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new ObjectMapper());
        assertThat(transport.sendTelegramDocumentGuarded(channel,42L,new MockMultipartFile("file","file.bin","application/octet-stream",new byte[]{1}),"",mode.equals("null")?null:()->false).classification()).isEqualTo("scope_denied");
        verify(http,never()).send(any(),any(HttpResponse.BodyHandler.class));
    }
}
