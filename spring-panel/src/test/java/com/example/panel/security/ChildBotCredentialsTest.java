package com.example.panel.security;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChildBotCredentialsTest {
    private static final String MASTER = "test-only-master-012345678901234567890123456789";
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void credentialsAreChannelPlatformAndDomainBound(String platform) {
        var child = new ChildBotCredentials.Principal(Long.MAX_VALUE, platform);
        String token = ChildBotCredentials.apiToken(MASTER, null, child);
        assertThat(ChildBotCredentials.authenticate(token, MASTER, null)).isEqualTo(child);
        assertThat(token).doesNotContain(MASTER);
        assertThat(ChildBotCredentials.authenticate(token, MASTER+"changed", null)).isNull();
        assertThat(ChildBotCredentials.authenticate(token.replace(":9223372036854775807:", ":22:"), MASTER, null)).isNull();
        assertThat(ChildBotCredentials.databasePassword(MASTER, null, child)).hasSize(64).isNotEqualTo(token.substring(token.lastIndexOf(':')+1));
        assertThat(child.databaseRole()).isEqualTo("iguana_child_9223372036854775807");
    }
    @ParameterizedTest @ValueSource(strings={"", " ", "telegram ", "Telegram", "other"})
    void unknownPlatformsNeverDefaultToTelegram(String platform) {
        assertThatThrownBy(() -> new ChildBotCredentials.Principal(22,platform)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"", "iguana-child-v1:max:0:" , "iguana-child-v1:max:01:", "iguana-child-v1:max:9223372036854775808:", "iguana-child-v1:MAX:22:"})
    void malformedTokenIsRejected(String prefix) {
        assertThat(ChildBotCredentials.authenticate(prefix+"a".repeat(64), MASTER, null)).isNull();
    }
    @Test void rotatingEitherControlPlaneSecretInvalidatesChildCredential() {
        var child = new ChildBotCredentials.Principal(22,"max");
        String signing="signature-test-only-012345678901234567890123456789";
        String token=ChildBotCredentials.apiToken(MASTER,signing,child);
        assertThat(ChildBotCredentials.authenticate(token,MASTER,signing)).isEqualTo(child);
        assertThat(ChildBotCredentials.authenticate(token,MASTER+"2",signing)).isNull();
        assertThat(ChildBotCredentials.authenticate(token,MASTER,signing+"2")).isNull();
    }
    @Test void defaultSecretCannotBootstrapProductionChildren() {
        assertThatThrownBy(() -> ChildBotCredentials.apiToken("iguana-internal-bot-token",null,new ChildBotCredentials.Principal(22,"max")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
