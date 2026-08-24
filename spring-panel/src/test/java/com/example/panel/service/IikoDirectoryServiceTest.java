package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.service.EmployeeDiscountAutomationCredentialService.IikoProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class IikoDirectoryServiceTest {

    @Test
    void emptyCategorySnapshotIsTreatedAsAlreadyRemovedOnReplay() throws Exception {
        AtomicInteger removeCalls = new AtomicInteger();
        HttpServer server = createServer(
            "{\"id\":\"customer-1\",\"categories\":[]}",
            500,
            "{\"error\":\"must not be called\"}",
            removeCalls
        );
        try {
            IikoDirectoryService service = serviceFor(server, List.of("cat-1"));

            IikoDirectoryService.MutationResult result = service.disableCorporateDiscount("alice", "+79991234567");

            assertThat(result.success()).isTrue();
            assertThat(result.message()).contains("категория cat-1 уже отсутствует");
            assertThat(removeCalls.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void duplicateCategoryRemovalHttpErrorIsIdempotentWhenApiSaysNotFound() throws Exception {
        AtomicInteger removeCalls = new AtomicInteger();
        HttpServer server = createServer(
            "{\"id\":\"customer-1\",\"categories\":[{\"id\":\"cat-1\",\"isActive\":true}]}",
            409,
            "{\"error\":\"Category not found\"}",
            removeCalls
        );
        try {
            IikoDirectoryService service = serviceFor(server, List.of("cat-1"));

            IikoDirectoryService.MutationResult result = service.disableCorporateDiscount("alice", "+79991234567");

            assertThat(result.success()).isTrue();
            assertThat(result.message()).contains("категория cat-1 уже отсутствует");
            assertThat(removeCalls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonIgnorableCategoryRemovalErrorStillFailsClosed() throws Exception {
        AtomicInteger removeCalls = new AtomicInteger();
        HttpServer server = createServer(
            "{\"id\":\"customer-1\",\"categories\":[{\"id\":\"cat-1\",\"isActive\":true}]}",
            403,
            "{\"error\":\"Permission denied\"}",
            removeCalls
        );
        try {
            IikoDirectoryService service = serviceFor(server, List.of("cat-1"));

            assertThatThrownBy(() -> service.disableCorporateDiscount("alice", "+79991234567"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Permission denied");
            assertThat(removeCalls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    private HttpServer createServer(String customerJson,
                                    int removalStatus,
                                    String removalBody,
                                    AtomicInteger removeCalls) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/0/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/auth/access_token")) {
                send(exchange, 200, "token-123", "text/plain; charset=UTF-8");
                return;
            }
            if (path.endsWith("/customers/get_customer_by_phone")) {
                send(exchange, 200, customerJson, "application/json; charset=UTF-8");
                return;
            }
            if (path.endsWith("/customers/customer-1/remove_category")) {
                removeCalls.incrementAndGet();
                send(exchange, removalStatus, removalBody, "application/json; charset=UTF-8");
                return;
            }
            send(exchange, 404, "{\"error\":\"unknown path\"}", "application/json; charset=UTF-8");
        });
        server.start();
        return server;
    }

    private IikoDirectoryService serviceFor(HttpServer server, List<String> categoryIds) {
        EmployeeDiscountAutomationCredentialService credentials = mock(EmployeeDiscountAutomationCredentialService.class);
        IikoProfile profile = new IikoProfile(
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "login",
            "secret",
            "org-1",
            categoryIds,
            List.of()
        );
        when(credentials.loadActiveIikoProfile("alice")).thenReturn(profile);
        return new IikoDirectoryService(credentials, new ObjectMapper());
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}