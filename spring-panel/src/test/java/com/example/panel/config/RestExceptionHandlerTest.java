package com.example.panel.config;

import com.example.panel.model.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTest {
    @Test void uploadFailureUsesActualLimitAndUnknownLimitUsesBoundConfiguration() {
        var handler = new RestExceptionHandler();
        var properties = new org.springframework.boot.autoconfigure.web.servlet.MultipartProperties();
        properties.setMaxFileSize(org.springframework.util.unit.DataSize.ofMegabytes(7));
        properties.setMaxRequestSize(org.springframework.util.unit.DataSize.ofMegabytes(9));
        handler.configureUploadLimits(properties);
        var request = new MockHttpServletRequest("POST", "/api/dialog-ticket/media");
        assertThat(handler.handleMaxUploadSize(new MaxUploadSizeExceededException(5 * 1024 * 1024), request).getBody().error()).contains("5 МБ").doesNotContain("50");
        assertThat(handler.handleMaxUploadSize(new MaxUploadSizeExceededException(-1), request).getBody().error()).contains("7 МБ", "9 МБ");
    }
    @Test void uploadIoAndMalformedMultipartReturnReadableJsonWithoutRawDetails() {
        var handler = new RestExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/api/operator-dialogs/1/attachments/upload");
        var io = handler.handleIo(new java.io.IOException("internal path and token"), request);
        assertThat(io.getBody()).isInstanceOf(ApiErrorResponse.class);
        assertThat(((ApiErrorResponse)io.getBody()).error()).doesNotContain("internal", "token");
        var malformed = handler.handleMultipart(new org.springframework.web.multipart.MultipartException("secret body"), request);
        assertThat(malformed.getStatusCode().value()).isEqualTo(400);
        assertThat(malformed.getBody().error()).contains("вложения").doesNotContain("secret");
    }
    @Test void ordinaryTimeoutHasJsonButBinaryDownloadStillHasNoJsonBody() {
        var handler = new RestExceptionHandler();
        var timeout = handler.handleAsyncTimeout(new AsyncRequestTimeoutException(), new MockHttpServletRequest("POST", "/api/dialog-ticket/reply"));
        assertThat(timeout.getBody()).isInstanceOf(ApiErrorResponse.class);
        var binary = handler.handleIo(new java.io.IOException("storage unavailable"), new MockHttpServletRequest("GET", "/api/operator-dialogs/1/attachments/file"));
        assertThat(binary.getBody()).isNull();
    }

    @Test
    void missingMultipartPartReturnsBadRequestEnvelope() {
        RestExceptionHandler handler = new RestExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/dialog-ticket/media");

        var response = handler.handleMissingPart(new MissingServletRequestPartException("file"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().errorCode()).isEqualTo("MISSING_PART");
        assertThat(response.getBody().error()).isEqualTo("Отсутствует обязательная часть запроса: file");
        assertThat(response.getBody().path()).isEqualTo("/api/dialog-ticket/media");
    }

    @Test
    void methodSecurityDenialReturnsForbiddenWithoutInternalDetails() {
        RestExceptionHandler handler = new RestExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dialog-ticket/notes");

        var response = handler.handleAccessDenied(new AccessDeniedException("internal authority details"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().errorCode()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().error()).isEqualTo("Недостаточно прав");
        assertThat(response.getBody().path()).isEqualTo("/api/dialog-ticket/notes");
    }

    @Test
    void handleMaxUploadSizeReturnsPayloadTooLarge() {
        RestExceptionHandler handler = new RestExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/dialogs/test/media");

        var response = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(50L * 1024L * 1024L), request);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.errorCode()).isEqualTo("FILE_TOO_LARGE");
        assertThat(body.error()).contains("50");
        assertThat(body.path()).isEqualTo("/api/dialogs/test/media");
    }

    @Test
    void handleAsyncTimeoutReturnsBodylessServiceUnavailableForEventStream() {
        RestExceptionHandler handler = new RestExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events/stream");
        request.addHeader("Accept", "text/event-stream");

        var response = handler.handleAsyncTimeout(new AsyncRequestTimeoutException(), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void handleUnexpectedReturnsBodylessResponseForEventStreamRequests() {
        RestExceptionHandler handler = new RestExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events/stream");
        request.addHeader("Accept", "text/event-stream");

        var response = handler.handleUnexpected(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNull();
    }
}
