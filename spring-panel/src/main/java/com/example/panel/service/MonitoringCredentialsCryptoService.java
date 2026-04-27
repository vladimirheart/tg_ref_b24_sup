package com.example.panel.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class MonitoringCredentialsCryptoService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringCredentialsCryptoService.class);

    private static final String PREFIX = "enc:v1:";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final SharedConfigService sharedConfigService;
    private final String configuredMasterKey;
    private final String keyFileName;
    private final SecureRandom secureRandom = new SecureRandom();
    private volatile SecretKey secretKey;

    public MonitoringCredentialsCryptoService(SharedConfigService sharedConfigService,
                                              @Value("${monitoring.credentials.master-key:}") String configuredMasterKey,
                                              @Value("${monitoring.credentials.key-file:monitoring-credentials.key}") String keyFileName) {
        this.sharedConfigService = sharedConfigService;
        this.configuredMasterKey = configuredMasterKey;
        this.keyFileName = keyFileName;
    }

    @PostConstruct
    void init() {
        this.secretKey = loadOrCreateSecretKey();
    }

    public boolean isEncrypted(String value) {
        return StringUtils.hasText(value) && value.startsWith(PREFIX);
    }

    public String encryptIfNeeded(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        if (isEncrypted(plainText)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt monitoring credential", ex);
        }
    }

    public String decryptIfNeeded(String storedValue) {
        if (!StringUtils.hasText(storedValue)) {
            return storedValue;
        }
        if (!isEncrypted(storedValue)) {
            return storedValue;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(storedValue.substring(PREFIX.length()));
            if (payload.length <= GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Encrypted monitoring credential payload is too short");
            }
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] cipherText = new byte[payload.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt monitoring credential", ex);
        }
    }

    private SecretKey loadOrCreateSecretKey() {
        try {
            if (StringUtils.hasText(configuredMasterKey)) {
                byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(configuredMasterKey.trim().getBytes(StandardCharsets.UTF_8));
                return new SecretKeySpec(derived, "AES");
            }

            Path keyPath = sharedConfigService.resolvePath(keyFileName);
            if (Files.isRegularFile(keyPath)) {
                String encoded = Files.readString(keyPath, StandardCharsets.UTF_8).trim();
                byte[] decoded = Base64.getDecoder().decode(encoded);
                return new SecretKeySpec(decoded, "AES");
            }

            Files.createDirectories(keyPath.getParent());
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256);
            SecretKey generatedKey = keyGenerator.generateKey();
            String encoded = Base64.getEncoder().encodeToString(generatedKey.getEncoded());
            Files.writeString(keyPath, encoded, StandardCharsets.UTF_8);
            log.info("Generated monitoring credentials key at {}", keyPath);
            return generatedKey;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize monitoring credentials crypto", ex);
        }
    }
}
