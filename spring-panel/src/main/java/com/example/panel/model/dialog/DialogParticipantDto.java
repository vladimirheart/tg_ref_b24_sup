package com.example.panel.model.dialog;

import org.springframework.util.StringUtils;

public record DialogParticipantDto(String username,
                                   String displayName,
                                   String avatarUrl,
                                   String department,
                                   String role,
                                   String addedAt,
                                   String addedBy) {

    public DialogParticipantDto {
        username = normalize(username);
        displayName = normalize(displayName);
        avatarUrl = normalize(avatarUrl);
        department = normalize(department);
        role = normalize(role);
        addedAt = normalize(addedAt);
        addedBy = normalize(addedBy);
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
