package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChildTransportEnvelopeTest {
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void rawWireAndPublisherHaveAnIndependentHmacProof(String platform) throws Exception {
        String token="iguana-child-v1:"+platform+":22:"+"a".repeat(64);byte[] body="{\"text\":\"Юникод\"}".getBytes(StandardCharsets.UTF_8);
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String expected=HexFormat.of().formatHex(mac.doFinal(("iguana-child-event-v1\niguana_child_22\n"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body))).getBytes(StandardCharsets.UTF_8)));
        assertThat(ChildTransportEnvelope.sign(token,"iguana_child_22",body)).isEqualTo(expected);
        assertThat(ChildTransportEnvelope.sign(token,"iguana_child_22",new byte[0])).isNotEqualTo(expected);
        assertThat(ChildTransportEnvelope.sign(token.replace("a".repeat(64),"b".repeat(64)),"iguana_child_22",body)).isNotEqualTo(expected);
    }
    @ParameterizedTest @ValueSource(strings={"", "shared-token", "iguana-child-v1:max:0:", "iguana-child-v1:MAX:22:"})
    void sharedOrInvalidSecretsCannotPublishASignedEnvelope(String token) {
        assertThatThrownBy(()->ChildTransportEnvelope.sign(token,"iguana_child_22",new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"r287_test","iguana_child_23","iguana_child_022","","iguana_child_22 "})
    void brokerIdentityMustMatchTheApiPrincipal(String publisher) {
        assertThatThrownBy(()->ChildTransportEnvelope.sign("iguana-child-v1:max:22:"+"a".repeat(64),publisher,new byte[0]))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
