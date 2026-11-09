package com.example.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.*;
import java.time.Duration;
import java.util.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.SocatContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Docker 29 does not publish internal-only ports. Keep services internal; relay only two fixed TCP targets. */
final class MaxMediaTestInfrastructure implements AutoCloseable {
    static final String RELAY_IMAGE = "alpine/socat:1.7.4.3-r0";
    final String nonce;
    final Network services, ingress;
    final GenericContainer<?> postgres, broker;
    final SocatContainer relay;
    // docker-java represents Docker API's ExposedPorts/Volumes {} markers as plain Object.
    // Keep this setting local to inspection serialization; every isolation guard below still runs.
    private static final ObjectMapper INSPECTION_MAPPER = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    static JsonNode containerInspection(InspectContainerResponse inspect) {
        return INSPECTION_MAPPER.valueToTree(inspect);
    }

    MaxMediaTestInfrastructure(String runNonce) {
        nonce = MaxMediaChainFixture.nonce(runNonce);
        services = network(true, "services");
        ingress = network(false, "ingress");
        postgres = new GenericContainer<>("postgres:16-alpine").withNetwork(services).withNetworkAliases("max-media-postgres")
            .withLabels(labels("postgres"))
            .withEnv("POSTGRES_DB", MaxMediaChainFixture.DATABASE).withEnv("POSTGRES_USER", "r287_test")
            .withEnv("POSTGRES_PASSWORD", MaxMediaChainFixture.TOKEN)
            .withCommand("postgres", "-c", "fsync=off")
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2)
                .withStartupTimeout(Duration.ofSeconds(90)))
            .withCreateContainerCmdModifier(c -> privateHost(c.getHostConfig(), 192));
        broker = new GenericContainer<>("rabbitmq:3.13-management-alpine").withNetwork(services).withNetworkAliases("max-media-rabbit")
            .withLabels(labels("rabbit"))
            .withEnv("RABBITMQ_DEFAULT_USER", "iguana_child_" + MaxMediaChainFixture.CHANNEL)
            .withEnv("RABBITMQ_DEFAULT_PASS", MaxMediaChainFixture.TOKEN)
            .withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS", "+S 1:1 +A 4 +sbwt none +sbwtdcpu none +sbwtdio none")
            .waitingFor(Wait.forLogMessage(".*Server startup complete;.*\\s", 1).withStartupTimeout(Duration.ofSeconds(90)))
            .withCreateContainerCmdModifier(c -> privateHost(c.getHostConfig(), 384));
        relay = new SocatContainer(DockerImageName.parse(RELAY_IMAGE)) {
            @Override protected void containerIsStarting(InspectContainerResponse info) {
                // Its primary network publishes localhost ports. Only this relay joins the internal service network.
                getDockerClient().connectToNetworkCmd().withNetworkId(services.getId()).withContainerId(getContainerId()).exec();
            }
        };
        relay.withNetwork(ingress).withLabels(labels("relay"))
            .withTarget(5432, "max-media-postgres").withTarget(5672, "max-media-rabbit")
            .withCreateContainerCmdModifier(c -> {
                c.withUser("65534:65534");
                c.getHostConfig().withMemory(64L * 1024 * 1024).withNanoCPUs(250_000_000L)
                    .withReadonlyRootfs(true).withCapDrop(Capability.ALL)
                    .withSysctls(Map.of("net.ipv4.ip_forward", "0"))
                    .withPortBindings(binding(5432), binding(5672));
            });
    }

    private Map<String, String> labels(String role) {
        return Map.of("io.iguana.owner", MaxMediaChainFixture.OWNER, "io.iguana.test-nonce", nonce, "io.iguana.fixture-role", role);
    }

    private Network network(boolean internal, String role) {
        return Network.builder().createNetworkCmdModifier(c -> c.withInternal(internal).withLabels(labels(role))).build();
    }

    static void privateHost(HostConfig config, int memoryMiB) {
        config.withMemory(memoryMiB * 1024L * 1024).withNanoCPUs(1_000_000_000L)
            .withPublishAllPorts(false).withPortBindings(new Ports());
    }

    private static PortBinding binding(int port) {
        return new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort.tcp(port));
    }

    void start() {
        postgres.start(); broker.start(); relay.start();
        var docker = relay.getDockerClient();
        verifyNetwork(INSPECTION_MAPPER.valueToTree(docker.inspectNetworkCmd().withNetworkId(services.getId()).exec()), true, "services", nonce);
        verifyNetwork(INSPECTION_MAPPER.valueToTree(docker.inspectNetworkCmd().withNetworkId(ingress.getId()).exec()), false, "ingress", nonce);
        verifyService(containerInspection(docker.inspectContainerCmd(postgres.getContainerId()).exec()), services.getId());
        verifyService(containerInspection(docker.inspectContainerCmd(broker.getContainerId()).exec()), services.getId());
        verifyRelay(containerInspection(docker.inspectContainerCmd(relay.getContainerId()).exec()), services.getId(), ingress.getId());
    }

    String jdbcUrl() { return "jdbc:postgresql://" + relay.getHost() + ":" + relay.getMappedPort(5432) + "/" + MaxMediaChainFixture.DATABASE; }
    String host() { return relay.getHost(); }
    int amqpPort() { return relay.getMappedPort(5672); }

    static void verifyNetwork(JsonNode inspect, boolean internal, String role, String nonce) {
        if (!inspect.path("Internal").isBoolean() || inspect.path("Internal").asBoolean() != internal
            || !inspect.path("Driver").asText().equals("bridge")
            || !inspect.path("Labels").path("io.iguana.owner").asText().equals(MaxMediaChainFixture.OWNER)
            || !inspect.path("Labels").path("io.iguana.test-nonce").asText().equals(nonce)
            || !inspect.path("Labels").path("io.iguana.fixture-role").asText().equals(role))
            throw new IllegalStateException("Owned fixture network isolation mismatch");
    }

    private static Set<String> networks(JsonNode inspect) {
        var result = new HashSet<String>(); var networks = inspect.path("NetworkSettings").path("Networks");
        if (!networks.isObject() || networks.isEmpty()) throw new IllegalStateException("Missing fixture network membership");
        networks.forEach(n -> { String id = n.path("NetworkID").asText(); if (id.isEmpty() || !result.add(id)) throw new IllegalStateException("Invalid fixture network membership"); });
        return result;
    }

    static void verifyService(JsonNode inspect, String servicesId) {
        if (!networks(inspect).equals(Set.of(servicesId))) throw new IllegalStateException("Service escaped the internal fixture network");
        var ports = inspect.path("NetworkSettings").path("Ports");
        if (!ports.isObject()) throw new IllegalStateException("Missing service port inspection");
        ports.forEach(p -> { if (!p.isNull() && (!p.isArray() || !p.isEmpty())) throw new IllegalStateException("Internal fixture service has a published port"); });
    }

    static void verifyRelay(JsonNode inspect, String servicesId, String ingressId) {
        if (!networks(inspect).equals(Set.of(servicesId, ingressId))) throw new IllegalStateException("Unexpected relay network membership");
        var ports = inspect.path("NetworkSettings").path("Ports");
        if (!ports.isObject() || ports.size() != 2) throw new IllegalStateException("Missing or extra relay ports");
        Set<Integer> assigned = new HashSet<>();
        for (String port : List.of("5432/tcp", "5672/tcp")) {
            var bindings = ports.path(port);
            if (!bindings.isArray() || bindings.size() != 1 || !bindings.get(0).path("HostIp").asText().equals("127.0.0.1"))
                throw new IllegalStateException("Relay requires a single loopback binding per port");
            String value = bindings.get(0).path("HostPort").asText();
            if (!value.matches("[0-9]{1,5}")) throw new IllegalStateException("Invalid relay host port");
            int hostPort = Integer.parseInt(value);
            if (hostPort <= 1024 || hostPort > 65535 || hostPort == 5432 || hostPort == 5672 || !assigned.add(hostPort))
                throw new IllegalStateException("Relay requires distinct nonstandard host ports");
        }
    }

    @Override public void close() {
        try { relay.stop(); } finally { try { broker.stop(); } finally { try { postgres.stop(); }
            finally { try { ingress.close(); } finally { services.close(); } } } }
    }
}
