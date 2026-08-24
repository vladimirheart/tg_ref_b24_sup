package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Utf8RuntimeEncodingTest {

    @Test
    void runtimeAndLogbackUseUtf8() throws Exception {
        assertThat(Charset.defaultCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(System.getProperty("file.encoding")).isEqualToIgnoringCase("UTF-8");

        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("logback-spring.xml")) {
            assertThat(stream).isNotNull();
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml).contains("<charset>UTF-8</charset>");
        }

        System.out.println("UTF-8 console smoke: Бот готов — журнал читается.");
    }
}