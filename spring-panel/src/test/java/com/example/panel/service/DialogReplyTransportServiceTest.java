package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.when;

class DialogReplyTransportServiceTest {

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
    void sendMediaRejectsFileLargerThanFiftyMegabytes() {
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
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

        assertThat(result.error()).isEqualTo("Файл слишком большой для Telegram. Максимальный размер — 50 МБ.");
        assertThat(result.telegramMessageId()).isNull();
        verify(integrationNetworkService, never()).createChannelHttpClient(any(), any(Duration.class));
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

        assertThat(result.error()).isEqualTo("Не удалось отправить файл в Telegram: превышено время ожидания загрузки.");
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

        assertThat(result.error()).isEqualTo("Telegram: Bad Request: wrong file identifier/HTTP URL specified");
        assertThat(result.error()).doesNotContain("Р");
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

        assertThat(result.error()).isEqualTo("Файл слишком большой для Telegram. Максимальный размер — 50 МБ.");
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

        assertThat(result.error()).isEqualTo("Не удалось отправить файл в Telegram. Проверьте сеть и повторите попытку.");
        assertThat(result.error()).doesNotContain("Р");
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
}
