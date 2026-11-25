package com.example.panel.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

/**
 * Supports the current panel data shape where legacy rows may still keep a
 * plain-text password in users.password, while new writes store bcrypt hashes.
 */
public class LegacyCompatiblePasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final String timingHash = bcrypt.encode("legacy-authentication-timing");

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String storedPassword) {
        if (!StringUtils.hasText(storedPassword)) {
            return false;
        }
        String raw = rawPassword == null ? "" : rawPassword.toString();
        String stored = storedPassword.trim();

        if (looksLikeBcrypt(stored)) {
            return bcrypt.matches(rawPassword, stored);
        }
        // A stored hash must never become a usable password via the legacy path.
        bcrypt.matches(raw, timingHash);
        return java.security.MessageDigest.isEqual(stored.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return !looksLikeBcrypt(encodedPassword);
    }

    private boolean looksLikeBcrypt(String value) {
        return value != null && value.matches("^\\$2[aby]\\$.{56}$");
    }
}
