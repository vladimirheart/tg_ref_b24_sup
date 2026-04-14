package com.example.panel.controller;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/max")
public class MaxWebhookRelayController {

    private static final Logger log = LoggerFactory.getLogger(MaxWebhookRelayController.class);

    private final ChannelRepository channelRepository;
    private final BotProcessProperties botProcessProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public MaxWebhookRelayController(ChannelRepository channelRepository,
                                     BotProcessProperties botProcessProperties) {
        this.channelRepository = channelRepository;
        this.botProcessProperties = botProcessProperties;
    }

    @PostMapping(value = "/{channelId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> relay(@PathVariable Long channelId,
                                        @RequestBody byte[] body,
                                        @RequestHeader(value = "X-Max-Bot-Api-Secret", required = false) String secret) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null || !"max".equalsIgnoreCase(Objects.toString(channel.getPlatform(), ""))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("MAX channel not found");
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(resolveLocalUri(channelId))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body != null ? body : new byte[0]));
        if (secret != null && !secret.isBlank()) {
            requestBuilder.header("X-Max-Bot-Api-Secret", secret);
        }

        try {
            HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            return ResponseEntity.status(response.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
        } catch (ConnectException ex) {
            log.warn("MAX webhook target for channel {} is unavailable: {}", channelId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("MAX bot process is not reachable");
        } catch (Exception ex) {
            log.warn("Failed to relay MAX webhook for channel {}", channelId, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Failed to relay MAX webhook");
        }
    }

    private URI resolveLocalUri(Long channelId) {
        String host = botProcessProperties.getHost();
        int port = botProcessProperties.resolveMaxPort(channelId);
        return URI.create("http://" + host + ":" + port + "/webhooks/max");
    }
}
