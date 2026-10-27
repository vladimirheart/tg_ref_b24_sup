package com.example.panel.security;

/** Validation for new credential writes; verification of existing credentials remains unchanged. */
public final class CredentialPasswordPolicy {
    private CredentialPasswordPolicy() {}

    public static String error(String password) {
        if (password.isEmpty() || password.matches("[\\s\\p{Z}\\uFEFF]*")) return "Пароль не может быть пустым или состоять только из пробелов.";
        if (password.codePoints().anyMatch(cp -> Character.isISOControl(cp) || cp >= 0xD800 && cp <= 0xDFFF)) {
            return "Пароль содержит недопустимые управляющие символы или некорректный Unicode. Удалите их.";
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            return "Пароль слишком длинный: максимум 72 байта UTF-8; кириллица и emoji занимают больше одного байта.";
        }
        return "";
    }

}
