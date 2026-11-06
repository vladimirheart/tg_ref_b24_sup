package com.example.panel.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Domain-separated credentials. A child receives neither control-plane secret. */
public final class ChildBotCredentials {
    private ChildBotCredentials() {}

    public record Principal(long channelId, String platform) {
        public Principal {
            if (channelId <= 0 || !Set.of("telegram", "vk", "max").contains(platform == null ? "" : platform)) {
                throw new IllegalArgumentException("Invalid child identity");
            }
        }
        public String databaseRole() { return "iguana_child_" + channelId; }
    }

    public static String apiToken(String masterToken, String signatureSecret, Principal child) {
        return "iguana-child-v1:" + child.platform() + ":" + child.channelId() + ":"
            + derive(masterToken, signatureSecret, "api", child);
    }

    public static String databasePassword(String masterToken, String signatureSecret, Principal child) {
        return derive(masterToken, signatureSecret, "sql", child);
    }

    public static String rabbitPassword(String masterToken, String signatureSecret, Principal child) {
        return derive(masterToken, signatureSecret, "rabbit", child);
    }

    public static Principal authenticate(String token, String masterToken, String signatureSecret) {
        try {
            if (token == null || !token.matches("iguana-child-v1:(telegram|vk|max):[1-9][0-9]{0,18}:[0-9a-f]{64}")) return null;
            String[] fields = token.split(":");
            Principal child = new Principal(Long.parseLong(fields[2]), fields[1]);
            return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                apiToken(masterToken, signatureSecret, child).getBytes(StandardCharsets.UTF_8)) ? child : null;
        } catch (IllegalArgumentException ex) { return null; }
    }

    public static boolean verifyEvent(Principal child, String masterToken, String signingSecret, String publisher, byte[] body, String signature) {
        if (body==null || signature==null || !signature.matches("[a-f0-9]{64}") || !child.databaseRole().equals(publisher)) return false;
        try {
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiToken(masterToken,signingSecret,child).getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] expected=mac.doFinal(("iguana-child-event-v1\n"+publisher+"\n"+digest).getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected,HexFormat.of().parseHex(signature));
        } catch(GeneralSecurityException | IllegalArgumentException ex) {return false;}
    }

    private static String derive(String token, String secret, String domain, Principal child) {
        String signingKey = secret != null && !secret.isBlank() ? secret.trim() : token;
        if (token == null || token.isBlank() || signingKey == null || signingKey.length() < 32) {
            throw new IllegalArgumentException("Child credentials require a configured control-plane secret of at least 32 characters");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("iguana-child-v1\n" + domain + "\n"
                + child.platform() + "\n" + child.channelId() + "\n" + token).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("Cannot derive child credentials", ex); }
    }
}
