package com.example.panel.model.dialog;

import org.springframework.util.StringUtils;

public record DialogOperatorOption(String username,
                                   String displayName,
                                   String avatarUrl,
                                   String department,
                                   String role) {

    public DialogOperatorOption {
        username = normalize(username);
        displayName = normalize(displayName);
        avatarUrl = normalize(avatarUrl);
        department = normalize(department);
        role = normalize(role);
    }

    public String displayLabel() {
        return StringUtils.hasText(displayName) ? displayName : username;
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
