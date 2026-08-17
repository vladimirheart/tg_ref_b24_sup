package com.example.panel.service.integration;

public record OutboundFeedbackPromptEvent(String eventId,
                                          String eventType,
                                          String correlationId,
                                          String platform,
                                          Long channelId,
                                          Long requestId,
                                          Long userId,
                                          String ticketId,
                                          String prompt) {
}
