package com.example.panel.model.publicform;

import java.util.Map;

public record PublicFormSubmission(String message,
                                   String clientName,
                                   String clientContact,
                                   String username,
                                   String captchaToken,
                                   Map<String, String> answers) {
}