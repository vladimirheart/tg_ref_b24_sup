package com.example.acceptance;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class MaxMediaHttpQueryTest {
    private static final long USER = 9_007_199_254_740_993L;
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Echo()).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @RestController static class Echo {
        @GetMapping("/probe") Map<String, Object> echo(@RequestParam long userId, @RequestParam String ticketId) {
            return Map.of("userId", userId, "ticketId", ticketId);
        }
    }

    @ParameterizedTest @ValueSource(strings = {
        " padded/Ю%?' ", " literal+plus ", " percent%20value ", " ampersand&equals=question? ",
        " Café/中文/😺 ", " tab\tline\nbreak ", " leading only", "trailing only "
    })
    void servletQueryDecodingPreservesExactRawRootAndSignedQuery(String raw) throws Exception {
        URI uri = URI.create("/probe?userId=" + USER + "&ticketId=" + URLEncoder.encode(raw, StandardCharsets.UTF_8));
        var result = mvc.perform(MaxMediaChainFixture.admissionRequest(uri)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getRequest().getQueryString()).isEqualTo(uri.getRawQuery());
        var body = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("ticketId").asText()).isEqualTo(raw);
        assertThat(body.path("userId").longValue()).isEqualTo(USER);
    }

    @Test void decodedRepeatedKeysKeepAllValuesWithoutChangingTheSignatureInput() throws Exception {
        URI uri = URI.create("/probe?userId=" + USER + "&%74icketId=first+value&ticketId=literal%2Bplus");
        var result = mvc.perform(MaxMediaChainFixture.admissionRequest(uri)).andReturn();
        assertThat(result.getRequest().getQueryString()).isEqualTo(uri.getRawQuery());
        assertThat(result.getRequest().getParameterValues("ticketId")).containsExactly("first value", "literal+plus");
    }
}
