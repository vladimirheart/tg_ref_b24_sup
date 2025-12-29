package com.example.panel.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;

final class RequestPayloadUtils {

    private RequestPayloadUtils() {
    }

    static Map<String, Object> readPayload(HttpServletRequest request, ObjectMapper objectMapper) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (!parameterMap.isEmpty()) {
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();
                if (values == null) {
                    payload.put(key, null);
                } else if (values.length == 1) {
                    payload.put(key, values[0]);
                } else {
                    payload.put(key, List.of(values));
                }
            }
        }

        String contentType = request.getContentType();
        boolean isJson = contentType != null
            && contentType.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE);
        if (isJson || payload.isEmpty()) {
            byte[] bodyBytes = request.getInputStream().readAllBytes();
            if (bodyBytes.length > 0) {
                try {
                    Map<String, Object> body = objectMapper.readValue(bodyBytes, Map.class);
                    if (body != null) {
                        payload.putAll(body);
                    }
                } catch (JsonProcessingException ex) {
                    if (payload.isEmpty()) {
                        throw ex;
                    }
                }
            }
        }

        return payload;
    }
}
