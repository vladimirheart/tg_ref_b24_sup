package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class MaxWebhookInputSupportTest {

    @Test
    void secretValidationPreservesBlankConfiguredSecretBehavior() {
        assertThat(MaxWebhookInputSupport.isSecretValid(null, null)).isTrue();
        assertThat(MaxWebhookInputSupport.isSecretValid("", "anything")).isTrue();
        assertThat(MaxWebhookInputSupport.isSecretValid("   ", "anything")).isTrue();
        assertThat(MaxWebhookInputSupport.isSecretValid("secret", "secret")).isTrue();
        assertThat(MaxWebhookInputSupport.isSecretValid("secret", "SECRET")).isFalse();
        assertThat(MaxWebhookInputSupport.isSecretValid("secret", null)).isFalse();
    }

    @Test
    void textExtractionPreservesMissingNullAndAsTextBehavior() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("update_type", "message_created");
        node.put("number", 7);
        node.putNull("nullable");

        assertThat(MaxWebhookInputSupport.text(null, "update_type")).isEmpty();
        assertThat(MaxWebhookInputSupport.text(node, "missing")).isEmpty();
        assertThat(MaxWebhookInputSupport.text(node, "nullable")).isEmpty();
        assertThat(MaxWebhookInputSupport.text(node, "update_type")).isEqualTo("message_created");
        assertThat(MaxWebhookInputSupport.text(node, "number")).isEqualTo("7");
    }

    @Test
    void longExtractionPreservesNumericTrimmedTextAndInvalidFallbacks() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("numeric", 42L);
        node.put("text", "  43  ");
        node.put("blank", "   ");
        node.put("invalid", "43x");
        node.putNull("nullable");

        assertThat(MaxWebhookInputSupport.asLong(null)).isNull();
        assertThat(MaxWebhookInputSupport.asLong(node.path("missing"))).isNull();
        assertThat(MaxWebhookInputSupport.asLong(node.path("nullable"))).isNull();
        assertThat(MaxWebhookInputSupport.asLong(node.path("numeric"))).isEqualTo(42L);
        assertThat(MaxWebhookInputSupport.asLong(node.path("text"))).isEqualTo(43L);
        assertThat(MaxWebhookInputSupport.asLong(node.path("blank"))).isNull();
        assertThat(MaxWebhookInputSupport.asLong(node.path("invalid"))).isNull();
    }
}
