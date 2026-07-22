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

        if (stored.equals(raw)) {
            return true;
        }
        if (looksLikeBcrypt(stored)) {
            return bcrypt.matches(rawPassword, stored);
        }
        return false;
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return !looksLikeBcrypt(encodedPassword);
    }

    private boolean looksLikeBcrypt(String value) {
        return value != null && value.matches("^\\$2[aby]\\$.{56}$");
    }
}
