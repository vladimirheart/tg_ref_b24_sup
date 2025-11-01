package com.example.panel.model.publicform;

import java.util.Map;

public record PublicFormSubmission(String message,
                                   String clientName,
                                   String clientContact,
                                   String username,
                                   Map<String, String> answers) {
}
