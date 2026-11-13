package com.example.panel.service;

import com.example.panel.config.IntegrationRabbitProperties;
import com.example.panel.security.ChildBotCredentials.Principal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** Backend-only management API. Never sends administrator credentials to a bot. */
@Service
public class ChildRabbitProvisioner {
    private final RabbitProperties rabbit;
    private final IntegrationRabbitProperties topology;
    private final Environment environment;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public ChildRabbitProvisioner(RabbitProperties rabbit, IntegrationRabbitProperties topology,
                                  Environment environment, ObjectMapper mapper) {
        this.rabbit = rabbit;
        this.topology = topology;
        this.environment = environment;
        this.mapper = mapper;
    }

    public void provision(Principal child, String passwordHash) {
        Session api = new Session();
        String vhost = encode(rabbit.getVirtualHost());
        String user = child.databaseRole();
        String queue = "iguana.integration.outbound.feedback-prompt." + child.platform() + ".channel." + child.channelId() + ".bot";
        String dlq = queue + ".dlq";
        String inbound = exchange(topology.getInboundExchange());
        String outbound = exchange(topology.getOutboundExchange());
        String deadLetters = exchange(topology.getOutboundDlx());
        // Backend topology is owned by RabbitAdmin; never replace exchanges here.
        for (String name : List.of(inbound, outbound, deadLetters)) {
            JsonNode actual = api.get("exchanges/" + vhost + "/" + encode(name));
            require(actual != null && actual.path("durable").asBoolean() && !actual.path("auto_delete").asBoolean()
                && "topic".equals(actual.path("type").asText()));
        }
        String userPath = "users/" + encode(user);
        JsonNode existing = api.get(userPath);
        if (existing == null) {
            api.write("PUT", userPath, Map.of("password_hash", passwordHash,
                "hashing_algorithm", "rabbit_password_hashing_sha256", "tags", List.of()));
            existing = api.get(userPath);
        }
        require(existing != null && passwordHash.equals(existing.path("password_hash").asText())
            && "rabbit_password_hashing_sha256".equals(existing.path("hashing_algorithm").asText())
            && emptyTags(existing.path("tags")));
        Map<String, Object> queueArguments = Map.of("x-dead-letter-exchange", deadLetters, "x-dead-letter-routing-key", dlq);
        ensureQueue(api, vhost, queue, queueArguments);
        ensureQueue(api, vhost, dlq, Map.of());
        ensureBinding(api, vhost, outbound, queue, topology.outboundFeedbackPromptRoutingKey(child.platform(), child.channelId()));
        ensureBinding(api, vhost, deadLetters, dlq, dlq);

        String permissionPath = "permissions/" + vhost + "/" + encode(user);
        JsonNode permissions = api.get("users/" + encode(user) + "/permissions");
        require(permissions != null && permissions.isArray() && permissions.size() <= 1);
        Map<String, String> expected = Map.of("configure", "^$", "write", exact(inbound), "read", exact(queue));
        if (permissions.isEmpty()) {
            api.write("PUT", permissionPath, expected);
            permissions = api.get("users/" + encode(user) + "/permissions");
        }
        require(permissions != null && permissions.size() == 1);
        JsonNode permission = permissions.get(0);
        require(rabbit.getVirtualHost().equals(permission.path("vhost").asText()));
        expected.forEach((key, value) -> require(value.equals(permission.path(key).asText())));
    }

    private void ensureQueue(Session api, String vhost, String name, Map<String, Object> arguments) {
        String path = "queues/" + vhost + "/" + encode(name);
        JsonNode actual = api.get(path);
        if (actual == null) {
            api.write("PUT", path, Map.of("durable", true, "auto_delete", false, "arguments", arguments));
            actual = api.get(path);
        }
        require(actual != null && actual.path("durable").asBoolean() && !actual.path("auto_delete").asBoolean()
            && !actual.path("exclusive").asBoolean() && mapper.valueToTree(arguments).equals(actual.path("arguments")));
    }

    private void ensureBinding(Session api, String vhost, String source, String queue, String routingKey) {
        JsonNode bindings = api.get("queues/" + vhost + "/" + encode(queue) + "/bindings");
        require(bindings != null && bindings.isArray());
        boolean found = false;
        for (JsonNode binding : bindings) {
            if (binding.path("source").asText().isEmpty()) continue; // implicit default-exchange binding
            require(source.equals(binding.path("source").asText()) && routingKey.equals(binding.path("routing_key").asText())
                && binding.path("arguments").isObject() && binding.path("arguments").isEmpty());
            found = true;
        }
        if (!found) api.write("POST", "bindings/" + vhost + "/e/" + encode(source) + "/q/" + encode(queue),
            Map.of("routing_key", routingKey, "arguments", Map.of()));
    }

    private static String exchange(String value) {
        require(value != null && value.matches("[A-Za-z0-9_.:-]{1,200}"));
        return value;
    }

    private static boolean emptyTags(JsonNode tags) {
        return (tags.isArray() && tags.isEmpty()) || (tags.isTextual() && tags.asText().isEmpty());
    }

    private static String exact(String value) {
        // RabbitMQ uses Erlang regex syntax; Java's \Q...\E is not portable.
        return "^" + value.replaceAll("([\\\\.\\[\\]{}()*+?^$|])", "\\\\$1") + "$";
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static void require(boolean valid) { if (!valid) throw new IllegalStateException("Child broker resource conflict"); }

    private class Session {
        private final long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        private final String base;
        private final String authorization;

        Session() {
            String configured = environment.getProperty("app.bots.infrastructure.rabbitmq-management-url",
                "http://" + rabbit.getHost() + ":15672");
            URI uri = URI.create(configured);
            require(List.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null);
            base = configured.replaceAll("/+$", "") + "/api/";
            String username = environment.getProperty("app.bots.infrastructure.rabbitmq-management-username", rabbit.getUsername());
            String password = environment.getProperty("app.bots.infrastructure.rabbitmq-management-password", rabbit.getPassword());
            authorization = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        }

        JsonNode get(String path) { return request("GET", path, null); }
        void write(String method, String path, Object body) { request(method, path, body); }

        private JsonNode request(String method, String path, Object body) {
            try {
                long remaining = deadline - System.nanoTime();
                require(remaining > 0);
                var request = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(3).toNanos())))
                    .header("Authorization", authorization).header("Content-Type", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if ("GET".equals(method) && response.statusCode() == 404) return null;
                require(response.statusCode() >= 200 && response.statusCode() < 300);
                return response.body().isBlank() ? mapper.nullNode() : mapper.readTree(response.body());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Child broker request interrupted");
            } catch (Exception ex) {
                throw new IllegalStateException("Child broker request failed");
            }
        }
    }
}
