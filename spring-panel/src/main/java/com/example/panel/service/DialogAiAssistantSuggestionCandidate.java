package com.example.panel.service;

public record DialogAiAssistantSuggestionCandidate(String source,
                                                   String title,
                                                   String snippet,
                                                   double score,
                                                   String memoryKey,
                                                   String status,
                                                   String trustLevel,
                                                   String sourceType,
                                                   String safetyLevel,
                                                   String sourceRef,
                                                   String intentKey,
                                                   String slotSignature,
                                                   String canonicalKey,
                                                   int evidenceCount,
                                                   String trace,
                                                   String updatedAt,
                                                   boolean stale) {
}
