package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.service.EmployeeDiscountAutomationCredentialService.Bitrix24Credentials;
import com.example.panel.service.EmployeeDiscountAutomationCredentialService.EmployeeDiscountAutomationCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class Bitrix24RestServiceTest {

    @Test
    void taskDiscoveryFollowsBitrixPagination() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger listCalls = new AtomicInteger();
        List<Integer> starts = new ArrayList<>();
        server.createContext("/rest/1/token/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Map<String, String> form = parseForm(readBody(exchange));
            if (path.endsWith("/tasks.task.list.json")) {
                listCalls.incrementAndGet();
                int start = Integer.parseInt(form.getOrDefault("start", "0"));
                starts.add(start);
                assertThat(form).containsEntry("filter[GROUP_ID]", "77");
                if (start == 0) {
                    sendJson(exchange, 200, "{\"result\":{\"tasks\":[{\"id\":\"101\"}]},\"next\":50}");
                } else {
                    sendJson(exchange, 200, "{\"result\":{\"tasks\":[{\"id\":\"202\"}]}}");
                }
                return;
            }
            if (path.endsWith("/tasks.task.get.json")) {
                String taskId = form.get("taskId");
                sendJson(exchange, 200, "{\"result\":{\"task\":{\"id\":\"" + taskId
                    + "\",\"title\":\"Task " + taskId + "\",\"description\":\"Тел. сотрудника: +79991234567\",\"status\":\"2\"}}}");
                return;
            }
            sendJson(exchange, 404, "{\"error\":\"unknown\"}");
        });
        server.start();
        try {
            Bitrix24RestService service = serviceFor(server, "alice");

            List<Map<String, Object>> tasks = service.listTasksForGroup("alice", 77L);

            assertThat(tasks).extracting(item -> item.get("id")).containsExactly("101", "202");
            assertThat(listCalls.get()).isEqualTo(2);
            assertThat(starts).containsExactly(0, 50);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void groupSearchUsesSubstringFilterAndFollowsPagination() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        List<Integer> starts = new ArrayList<>();
        server.createContext("/rest/1/token/", exchange -> {
            Map<String, String> form = parseForm(readBody(exchange));
            int start = Integer.parseInt(form.getOrDefault("start", "0"));
            starts.add(start);
            assertThat(form).containsEntry("FILTER[%NAME]", "Увольнение");
            if (start == 0) {
                sendJson(exchange, 200, "{\"result\":[{\"ID\":\"7\",\"NAME\":\"Увольнение Москва\"}],\"next\":50}");
            } else {
                sendJson(exchange, 200, "{\"result\":[{\"ID\":\"8\",\"NAME\":\"Увольнение Регионы\"}]}");
            }
        });
        server.start();
        try {
            Bitrix24RestService service = serviceFor(server, "alice");

            List<Map<String, Object>> groups = service.listWorkgroups("alice", "Увольнение", 10);

            assertThat(groups).extracting(item -> item.get("id")).containsExactly("7", "8");
            assertThat(starts).containsExactly(0, 50);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void checklistCompleteUsesPositionalParametersAndRequiresTrueResult() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        List<String> bodies = new ArrayList<>();
        server.createContext("/rest/1/token/", exchange -> {
            bodies.add(readBody(exchange));
            sendJson(exchange, 200, "{\"result\":true}");
        });
        server.start();
        try {
            Bitrix24RestService service = serviceFor(server, "alice");

            service.completeChecklistItem("alice", "101", "501");

            assertThat(bodies).containsExactly("0=101&1=501");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void checklistCompleteFailsClosedWhenBitrixReturnsFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/rest/1/token/", exchange -> sendJson(exchange, 200, "{\"result\":false}"));
        server.start();
        try {
            Bitrix24RestService service = serviceFor(server, "alice");

            assertThatThrownBy(() -> service.completeChecklistItem("alice", "101", "501"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("did not confirm checklist completion");
        } finally {
            server.stop(0);
        }
    }

    private Bitrix24RestService serviceFor(HttpServer server, String username) {
        EmployeeDiscountAutomationCredentialService credentials = mock(EmployeeDiscountAutomationCredentialService.class);
        String webhook = "http://127.0.0.1:" + server.getAddress().getPort() + "/rest/1/token/";
        EmployeeDiscountAutomationCredentials stored = new EmployeeDiscountAutomationCredentials(
            new Bitrix24Credentials("https://portal.example", webhook),
            "",
            Map.of()
        );
        when(credentials.loadForUser(username)).thenReturn(stored);
        return new Bitrix24RestService(credentials, new ObjectMapper());
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseForm(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}