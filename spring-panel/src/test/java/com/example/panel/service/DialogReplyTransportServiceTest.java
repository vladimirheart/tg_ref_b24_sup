package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogReplyTransportServiceTest {

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
    }

    @Test
    void sendMediaReturnsTelegramErrorDescriptionWithoutMojibake() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
        when(integrationNetworkService.createChannelHttpClient(any(), any(Duration.class))).thenReturn(httpClient);

        List<String> methodCalls = new ArrayList<>();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> responseFor(invocation.getArgument(0), methodCalls,
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

        assertThat(result.error()).isEqualTo("Telegram: Bad Request: file is too big");
        assertThat(result.error()).doesNotContain("Р");
        assertThat(result.telegramMessageId()).isNull();
        assertThat(methodCalls).containsExactly("sendDocument");
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

        assertThat(result.error()).isEqualTo("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043e\u0442\u043f\u0440\u0430\u0432\u0438\u0442\u044c \u0444\u0430\u0439\u043b \u0432 Telegram.");
        assertThat(result.error()).doesNotContain("Р");
        assertThat(result.telegramMessageId()).isNull();
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

    private static Channel telegramChannel() {
        Channel channel = new Channel();
        channel.setPlatform("telegram");
        channel.setToken("123456:test-token");
        return channel;
    }
}
