package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import com.example.panel.config.IntegrationRabbitProperties;
import com.example.panel.security.ChildBotCredentials.Principal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.mock.env.MockEnvironment;

class ChildRabbitProvisionerTest {
    static final Principal CHILD = new Principal(9007199254740993L, "max");
    static final String HASH = ChildInfrastructureService.rabbitHash("test-only-password");

    @Test void createsExactRestrictedResourcesAndRetryDoesNotRotateOrRedeclare() throws Exception {
        try (var api = new Broker()) {
            var client = api.client();
            client.provision(CHILD,HASH);
            assertThat(api.userPuts).isEqualTo(1);
            assertThat(api.permissions.get(0).path("configure").asText()).isEqualTo("^$");
            assertThat(api.permissions.get(0).path("write").asText()).isEqualTo("^iguana\\.integration\\.inbound$");
            assertThat(api.permissions.get(0).path("read").asText()).isEqualTo("^iguana\\.integration\\.outbound\\.feedback-prompt\\.max\\.channel\\.9007199254740993\\.bot$");
            assertThat(api.queues).hasSize(2);
            assertThat(api.bindings.values().stream().mapToInt(List::size).sum()).isEqualTo(2);
            int writes = api.writes;
            client.provision(CHILD,HASH);
            assertThat(api.writes).isEqualTo(writes);
        }
    }

    @Test void lostUserCreationResponseCanBeRetriedWithSameHash() throws Exception {
        try (var api = new Broker()) {
            api.failAfterUserCreate = true;
            assertThatThrownBy(() -> api.client().provision(CHILD,HASH)).isInstanceOf(IllegalStateException.class);
            api.client().provision(CHILD,HASH);
            assertThat(api.userPuts).isEqualTo(1);
        }
    }

    @ParameterizedTest @ValueSource(strings={"password","tags","permission","extra-vhost","queue","binding"})
    void conflictingExistingResourceIsNeverOverwritten(String conflict) throws Exception {
        try (var api = new Broker()) {
            var client=api.client();client.provision(CHILD,HASH);
            switch(conflict) {
                case "password" -> ((com.fasterxml.jackson.databind.node.ObjectNode)api.user).put("password_hash","foreign");
                case "tags" -> ((com.fasterxml.jackson.databind.node.ObjectNode)api.user).put("tags","administrator");
                case "permission" -> ((com.fasterxml.jackson.databind.node.ObjectNode)api.permissions.get(0)).put("read",".*");
                case "extra-vhost" -> api.permissions.add(api.mapper.valueToTree(Map.of("vhost","other")));
                case "queue" -> ((com.fasterxml.jackson.databind.node.ObjectNode)api.queues.values().iterator().next()).put("durable",false);
                case "binding" -> api.bindings.values().iterator().next().add(api.mapper.valueToTree(Map.of("source","foreign","routing_key","#","arguments",Map.of())));
            }
            int writes=api.writes;
            assertThatThrownBy(() -> client.provision(CHILD,HASH)).isInstanceOf(IllegalStateException.class);
            assertThat(api.writes).isEqualTo(writes);
        }
    }

    @Test void missingBackendExchangePreventsUserCreation() throws Exception {
        try(var api=new Broker()) {
            api.missingExchange=true;
            assertThatThrownBy(() -> api.client().provision(CHILD,HASH)).isInstanceOf(IllegalStateException.class);
            assertThat(api.writes).isZero();
        }
    }

    /** Stateful loopback management API double; no real RabbitMQ/provider access. */
    static class Broker implements AutoCloseable {
        final ObjectMapper mapper=new ObjectMapper();
        final HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        final Map<String,JsonNode> queues=new HashMap<>();
        final Map<String,List<JsonNode>> bindings=new HashMap<>();
        final List<JsonNode> permissions=new ArrayList<>();
        JsonNode user;
        int writes,userPuts;
        boolean failAfterUserCreate,missingExchange;

        Broker() throws Exception {
            server.createContext("/api/", request -> {
                int status=200;Object result=Map.of();
                try {
                    String auth="Basic "+Base64.getEncoder().encodeToString("test-admin:test-admin-password".getBytes(StandardCharsets.UTF_8));
                    if(!auth.equals(request.getRequestHeaders().getFirst("Authorization"))) throw new AssertionError("Unexpected management credentials");
                    String path=request.getRequestURI().getRawPath().substring(5),method=request.getRequestMethod();
                    String[] parts=path.split("/");
                    if(!method.equals("GET")) writes++;
                    JsonNode body=method.equals("GET")?null:mapper.readTree(request.getRequestBody());
                    if(parts[0].equals("exchanges")) {
                        if(missingExchange)status=404;
                        result=Map.of("type","topic","durable",true,"auto_delete",false);
                    } else if(parts[0].equals("users") && parts.length==2) {
                        if(method.equals("PUT")) {
                            userPuts++;user=body;
                            if(failAfterUserCreate){failAfterUserCreate=false;status=500;}
                        }
                        if(user==null)status=404;else result=user;
                    } else if(parts[0].equals("users") && parts[2].equals("permissions")) {
                        result=permissions;
                    } else if(parts[0].equals("permissions")) {
                        var permission=(com.fasterxml.jackson.databind.node.ObjectNode)body;
                        permission.put("vhost",URLDecoder.decode(parts[1],StandardCharsets.UTF_8));
                        permissions.clear();permissions.add(permission);
                    } else if(parts[0].equals("queues")) {
                        String key=parts[2];
                        if(parts.length==4) result=bindings.computeIfAbsent(key,k->new ArrayList<>());
                        else {
                            if(method.equals("PUT")){queues.put(key,body);}
                            if(!queues.containsKey(key))status=404;else result=queues.get(key);
                        }
                    } else if(parts[0].equals("bindings")) {
                        var binding=(com.fasterxml.jackson.databind.node.ObjectNode)body;
                        binding.put("source",URLDecoder.decode(parts[3],StandardCharsets.UTF_8));
                        bindings.computeIfAbsent(parts[5],k->new ArrayList<>()).add(binding);
                    } else throw new AssertionError("Unexpected endpoint");
                } catch(Throwable ex) { status=500;result=Map.of("error","fixture failure"); }
                byte[] bytes=mapper.writeValueAsBytes(result);
                request.sendResponseHeaders(status,bytes.length);
                try(var out=request.getResponseBody()){out.write(bytes);}
            });
            server.start();
        }

        ChildRabbitProvisioner client() {
            var rabbit=new RabbitProperties();rabbit.setVirtualHost("/");rabbit.setUsername("test-admin");rabbit.setPassword("test-admin-password");
            var topology=new IntegrationRabbitProperties();
            topology.setInboundExchange("iguana.integration.inbound");topology.setOutboundExchange("iguana.integration.outbound");topology.setOutboundDlx("iguana.integration.outbound.dlx");
            var env=new MockEnvironment().withProperty("app.bots.infrastructure.rabbitmq-management-url","http://127.0.0.1:"+server.getAddress().getPort());
            return new ChildRabbitProvisioner(rabbit,topology,env,mapper);
        }
        public void close(){server.stop(0);}
    }
}
