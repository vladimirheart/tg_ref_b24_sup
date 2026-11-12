package com.example.panel.security;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import static org.assertj.core.api.Assertions.assertThat;

class SettingsSecretMaskerTest {
    @Test void recursiveRedactionPreservesTheSourceAndSavedSecretIndicators() {
        var original = Map.of("token", "bot-secret", "platform_config", Map.of("secret", "callback-secret"),
                "profiles", List.of(Map.of("password", "proxy-secret", "username", "operator")),
                "api_token", "", "api_token_saved", true, "masked_token", "****1234");
        var masked = (Map<?, ?>) SettingsSecretMasker.redact(original);
        assertThat(masked.get("token")).isEqualTo("");
        assertThat(masked.get("token_saved")).isEqualTo(true);
        assertThat(masked.get("platform_config")).isEqualTo(Map.of("secret", "", "secret_saved", true));
        assertThat(masked.get("profiles")).isEqualTo(List.of(Map.of("password", "", "password_saved", true, "username", "operator")));
        assertThat(masked.get("api_token_saved")).isEqualTo(true);
        assertThat(masked.get("masked_token")).isEqualTo("****1234");
        assertThat(original.get("token")).isEqualTo("bot-secret");
    }

    @Test void aBlockEditorCanInspectThatBlocksCredentials() {
        var actor = new UsernamePasswordAuthenticationToken("editor", "", AuthorityUtils.createAuthorityList("PAGE_SETTINGS", "EDIT_SETTINGS_CHANNELS"));
        var value = Map.of("token", "bot-secret");
        assertThat(SettingsSecretMasker.forBlock(value, "channels", actor)).isSameAs(value);
        assertThat(SettingsSecretMasker.forBlock(value, "dialogs", actor)).isEqualTo(Map.of("token", "", "token_saved", true));
    }

    @Test void aMixedPageSectionUsesEachBlocksOwnPermission() {
        var actor = new UsernamePasswordAuthenticationToken("editor", "", AuthorityUtils.createAuthorityList("PAGE_SETTINGS", "EDIT_SETTINGS_NETWORK_PROFILES"));
        var result = SettingsSecretMasker.section(Map.of(
                "integrationNetworkProfiles", List.of(Map.of("password", "proxy-secret")),
                "botSettings", Map.of("api_key", "bot-secret")), actor);
        assertThat(result.get("integrationNetworkProfiles")).isEqualTo(List.of(Map.of("password", "proxy-secret")));
        assertThat(result.get("botSettings")).isEqualTo(Map.of("api_key", "", "api_key_saved", true));
    }
}
