package com.example.supportbot.max;

import com.example.supportbot.config.MaxBotProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaxApiClientTest {

    private final MaxApiClient client = new MaxApiClient(new MaxBotProperties());

    @Test
    void acceptsOnlyHttpsUrlsFromMaxMediaPerimeter() throws Exception {
        assertThat(client.validateAttachmentUri("https://iu.oneme.ru/upload.do?token=1").getHost()).isEqualTo("iu.oneme.ru");
        assertThat(client.validateAttachmentUri("https://omub.okcdn.ru/media/file.mp4").getHost()).isEqualTo("omub.okcdn.ru");
        assertThat(client.validateAttachmentUri("https://cdn.max.ru/media/file.jpg").getHost()).isEqualTo("cdn.max.ru");
    }

    @Test
    void rejectsUntrustedOrNonTlsAttachmentUrls() {
        assertThatThrownBy(() -> client.validateAttachmentUri("http://iu.oneme.ru/upload.do"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.validateAttachmentUri("https://example.org/file.jpg"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.validateAttachmentUri("https://max.ru@example.org/file.jpg"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
