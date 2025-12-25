package com.example.panel.model.clients;

public record ClientPhoneEntry(
        Long id,
        String phone,
        String label,
        String source,
        Boolean active,
        String createdAt,
        String createdBy
) {
}
