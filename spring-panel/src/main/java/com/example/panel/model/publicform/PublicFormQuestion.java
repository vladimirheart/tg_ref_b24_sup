package com.example.panel.model.publicform;

import java.util.Map;

public record PublicFormQuestion(String id,
                                 String text,
                                 String type,
                                 Integer order,
                                 Map<String, Object> metadata) {
}
