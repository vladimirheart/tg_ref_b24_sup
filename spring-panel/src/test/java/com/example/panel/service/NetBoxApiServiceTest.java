package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NetBoxApiServiceTest {

    private final NetBoxApiService service = new NetBoxApiService(new ObjectMapper());

    @Test
    void summarizeErrorBodyPrefersHtmlTitleOverRawMarkup() {
        String body = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <title>Server Error</title>
                    <meta charset="UTF-8">
                </head>
                <body>
                    <div class="container-fluid">Trace payload</div>
                </body>
                </html>
                """;

        assertEquals("Server Error", service.summarizeErrorBody(body));
    }

    @Test
    void summarizeErrorBodyCompactsPlainTextPayload() {
        String body = "  line one\\n\\n   line two   line three  ";

        assertEquals("line one line two line three", service.summarizeErrorBody(body));
    }
}
