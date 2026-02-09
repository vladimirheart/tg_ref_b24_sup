package com.example.panel.model.clients;

public record UnblockRequestItem(
        long id,
        String userId,
        String channelName,
        String reason,
        String status,
        String createdAt,
        String decidedAt,
        String decidedBy,
        String decisionComment
) {}
