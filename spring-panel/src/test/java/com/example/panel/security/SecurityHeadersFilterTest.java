package com.example.panel.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {
    private MockHttpServletResponse request(String method, String path, String ticketId) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (ticketId != null) request.addParameter("ticketId", ticketId);
        var response = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(request, response, (req, res) -> {});
        return response;
    }

    @Test
    void canonicalDialogPagesAllowOnlySameOriginFraming() throws Exception {
        for (String path : new String[]{"/dialogs", "/general-dialog-work/1234567890abcdef1234567890abcdef"}) {
            var response = request("GET", path, "ticket-1");
            assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
            assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("frame-ancestors 'self'");
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        }
    }

    @Test
    void otherPagesApisAndMutationResponsesKeepDeny() throws Exception {
        for (String path : new String[]{"/dialogs", "/dialogs/ticket-1", "/api/dialogs/ticket-1", "/settings",
                "/general-dialog-work/invalid", "/general-dialog-work/1234567890abcdef1234567890abcdef/history"}) {
            assertThat(request("GET", path, null).getHeader("X-Frame-Options")).isEqualTo("DENY");
        }
        assertThat(request("GET", "/dialogs", " ").getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(request("POST", "/dialogs", "ticket-1").getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(request("POST", "/general-dialog-work/1234567890abcdef1234567890abcdef", null).getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void typedDocumentPreviewStillOptsInOnlyExplicitly() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/object_passports/1/documents/12345678-1234-1234-1234-123456789012/revisions/1/file");
        req.addParameter("preview", "true");
        var response = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(req, response, (request, wrapped) ->
                ((jakarta.servlet.http.HttpServletResponse) wrapped).addHeader("X-Frame-Options", "SAMEORIGIN"));
        assertThat(response.getHeaders("X-Frame-Options")).containsExactly("SAMEORIGIN");
        assertThat(request("GET", "/api/object_passports/1/documents/12345678-1234-1234-1234-123456789012/revisions/1/file", null)
                .getHeader("X-Frame-Options")).isEqualTo("DENY");
    }
}
