package com.example.supportbot.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Persistent raw-wire proof; the secret is never carried in AMQP headers. */
public final class ChildTransportEnvelope {
    public static final String HEADER="X-Iguana-Child-Event-Signature";
    private ChildTransportEnvelope() {}
    public static String sign(String token,String publisher,byte[] body) {
        if(token==null || !token.matches("iguana-child-v1:(telegram|vk|max):[1-9][0-9]{0,18}:[0-9a-f]{64}")
                || publisher==null || !publisher.equals("iguana_child_"+token.split(":")[2]) || body==null)
            throw new IllegalArgumentException("Channel-bound event credentials required");
        try {
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("iguana-child-event-v1\n"+publisher+"\n"+digest).getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.GeneralSecurityException ex) {throw new IllegalStateException("Cannot sign child event",ex);}
    }
}
