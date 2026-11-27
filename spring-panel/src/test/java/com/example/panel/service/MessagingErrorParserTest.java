package com.example.panel.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MessagingErrorParserTest {
    @Test void telegramQuotaComesFromJsonEvenWhenHttpIs200() {
        var failure = MessagingErrorParser.parse("telegram", 200, null, null,
            "{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\",\"parameters\":{\"retry_after\":8}}");
        assertThat(failure.classification()).isEqualTo("rate_limited");
        assertThat(failure.message()).contains("Telegram", "8 сек", "429");
    }
    @Test void maxNotReadyIsTransientBeforeGenericHttp400Classification() {
        var failure = MessagingErrorParser.parse("max", 400, null, null,
            "{\"code\":\"attachment.not.ready\",\"message\":\"Key: errors.process.attachment.file.not.processed\"}");
        assertThat(failure.retryState()).isEqualTo("transient");
        assertThat(failure.message()).contains("MAX", "обрабатывает").doesNotContain("Telegram");
    }
    @Test void sizeAndTextErrorsNeverInventProviderLimitsOrWrongPlatform() {
        for (String platform : java.util.List.of("telegram", "vk", "max")) {
            var failure = MessagingErrorParser.parse(platform, 413, null, null, "<html>too large</html>");
            assertThat(failure.message()).contains("размер").doesNotContain("50 МБ", "<html>");
            assertThat(failure.retryState()).isEqualTo("terminal");
        }
        assertThat(MessagingErrorParser.parse("vk",200,null,null,"{\"error\":{\"error_code\":914,\"error_msg\":\"Message is too long\"}}").message()).contains("длинный текст");
    }
    @Test void vkErrorOnNon2xxStillPreservesActualCodeAndPermissionReason() {
        var failure = MessagingErrorParser.parse("vk", 403, null, "VK HTTP 403", "{\"error\":{\"error_code\":901,\"error_msg\":\"Can't send messages for users without permission\"}}");
        assertThat(failure.message()).contains("901", "Получатель", "Can't send");
        assertThat(failure.severity()).isEqualTo("warning");
    }
    @Test void unavailableAndMalformedRepliesNeverConfirmSuccess() {
        for (String body : java.util.List.of("", "not-json", "{}", "null", "{\"ok\":false}", "<html>gateway</html>"))
            for (String platform : java.util.List.of("telegram","vk","max")) assertThat(MessagingErrorParser.confirmedSuccess(platform,body)).isFalse();
        assertThat(MessagingErrorParser.confirmedSuccess("telegram","{\"ok\":true,\"result\":true}")).isTrue();
        assertThat(MessagingErrorParser.confirmedSuccess("vk","{\"response\":123}")).isTrue();
        assertThat(MessagingErrorParser.confirmedSuccess("max","{\"message\":{\"body\":{\"mid\":\"123\"}}}")).isTrue();
        assertThat(MessagingErrorParser.parse("telegram",502,null,null,"<html>bad gateway</html>").message()).contains("не подтвержден").doesNotContain("<html>");
    }
    @Test void vkLogicalCodesClassifyProviderFailureEvenWithHttp200() {
        for (int code : java.util.List.of(10,13,36)) {
            var failure = MessagingErrorParser.parse("vk",200,String.valueOf(code),"Internal server error",null);
            assertThat(failure.retryState()).isEqualTo("transient");
            assertThat(failure.severity()).isEqualTo("critical");
            assertThat(failure.message()).contains("не подтвержден");
        }
        assertThat(MessagingErrorParser.parse("vk",200,"29","Rate limit reached",null).classification()).isEqualTo("rate_limited");
        assertThat(MessagingErrorParser.parse("vk",200,"27","Group authorization failed",null).message()).contains("ключ и права");
    }
    @Test void allowlistedDescriptionDoesNotExposeRequestParamsOrCredentials() {
        var failure = MessagingErrorParser.parse("telegram",400,null,null,
            "{\"description\":\"bad token=private https://api.test/bot123:secret/send <script>x</script>\",\"request_params\":{\"password\":\"other-private\"}}");
        assertThat(failure.message()).doesNotContain("private", "https://", "<script>", "request_params");
        assertThat(MessagingErrorParser.safe("Authorization: Bearer private 123456:ABCDEFGHIJKLMNOPQRSTUVWXYZ12345")).doesNotContain("private","ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        assertThat(MessagingErrorParser.safe("{\"Authorization\":\"Bearer private\"} Authorization: Bearer private")).doesNotContain("private");
    }
}
