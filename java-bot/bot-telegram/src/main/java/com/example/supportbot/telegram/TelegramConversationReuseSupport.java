package com.example.supportbot.telegram;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the safe subset of a prior request that a client may reuse. */
final class TelegramConversationReuseSupport {

    private TelegramConversationReuseSupport() {
    }

    static Map<String, String> answers(String business,
                                       String locationType,
                                       String city,
                                       String locationName) {
        Map<String, String> answers = new LinkedHashMap<>();
        putIfPresent(answers, "business", business);
        putIfPresent(answers, "location_type", locationType);
        putIfPresent(answers, "city", city);
        putIfPresent(answers, "location_name", locationName);
        return Collections.unmodifiableMap(answers);
    }

    private static void putIfPresent(Map<String, String> answers, String key, String value) {
        if (value != null && !value.isBlank()) {
            answers.put(key, value);
        }
    }
}
