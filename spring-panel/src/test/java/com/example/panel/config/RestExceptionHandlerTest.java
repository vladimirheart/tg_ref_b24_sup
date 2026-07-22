package com.example.panel.config;

import com.example.panel.model.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTest {

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
