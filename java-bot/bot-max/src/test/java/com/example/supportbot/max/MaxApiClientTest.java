package com.example.supportbot.max;

import com.example.supportbot.config.MaxBotProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    void parsesDialogUserWithAvatarUrls() throws Exception {
        var root = new ObjectMapper().readTree("""
            {
              "chat_id": 2002,
              "type": "dialog",
              "dialog_with_user": {
                "user_id": 1001,
                "avatar_url": "https://cdn.max.ru/avatar-small.jpg",
                "full_avatar_url": "https://cdn.max.ru/avatar-full.jpg"
              }
            }
            """);

        var profile = client.parseDialogUser(root).orElseThrow();

        assertThat(profile.userId()).isEqualTo(1001L);
        assertThat(profile.avatarUrl()).isEqualTo("https://cdn.max.ru/avatar-small.jpg");
        assertThat(profile.fullAvatarUrl()).isEqualTo("https://cdn.max.ru/avatar-full.jpg");
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
