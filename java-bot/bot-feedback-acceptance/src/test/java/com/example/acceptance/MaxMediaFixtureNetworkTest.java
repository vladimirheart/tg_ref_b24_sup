package com.example.acceptance;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.Ports;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Actual docker-java inspect JSON roundtrips; no Docker required and no weakened acceptance matrix. */
class MaxMediaFixtureNetworkTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String NONCE = "a".repeat(32), SERVICES = "service-network", INGRESS = "ingress-network";

    @Test void startupDoesNotWaitForHostMappingsOnInternalServices() {
        var infrastructure = new MaxMediaTestInfrastructure(NONCE);
        assertThat(infrastructure.postgres.getExposedPorts()).isEmpty();
        assertThat(infrastructure.broker.getExposedPorts()).isEmpty();
        assertThat(infrastructure.postgres.getNetwork()).isSameAs(infrastructure.services);
        assertThat(infrastructure.broker.getNetwork()).isSameAs(infrastructure.services);
        assertThat(infrastructure.relay.getNetwork()).isSameAs(infrastructure.ingress);
        assertThat(infrastructure.relay.getExposedPorts()).containsExactlyInAnyOrder(5432, 5672);
        for (var container : List.of(infrastructure.postgres, infrastructure.broker, infrastructure.relay)) {
            assertThat(container.getLabels()).containsEntry("io.iguana.owner", MaxMediaChainFixture.OWNER)
                .containsEntry("io.iguana.test-nonce", NONCE);
        }
    }

    private ObjectNode container(boolean relay) {
        var root = mapper.createObjectNode(); var settings = root.putObject("NetworkSettings");
        var networks = settings.putObject("Networks"); networks.putObject("services").put("NetworkID", SERVICES);
        if (relay) networks.putObject("ingress").put("NetworkID", INGRESS);
        var ports = settings.putObject("Ports");
        if (relay) { ports.putArray("5432/tcp").addObject().put("HostIp", "127.0.0.1").put("HostPort", "15432");
            ports.putArray("5672/tcp").addObject().put("HostIp", "127.0.0.1").put("HostPort", "15672"); }
        else ports.putNull("5432/tcp");
        return root;
    }

    private ObjectNode roundtrip(ObjectNode inspect) throws Exception {
        return (ObjectNode) MaxMediaTestInfrastructure.containerInspection(mapper.treeToValue(inspect, InspectContainerResponse.class));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void internalServiceMayHaveUnpublishedImagePorts(boolean emptyArray) throws Exception {
        var inspect = container(false);
        if (emptyArray) ((ObjectNode) inspect.path("NetworkSettings").path("Ports")).putArray("5432/tcp");
        MaxMediaTestInfrastructure.verifyService(roundtrip(inspect), SERVICES);
    }

    @ParameterizedTest @ValueSource(strings = {"extra-network", "foreign-network", "empty-network", "missing-network", "published-loopback", "published-public", "missing-ports"})
    void serviceNetworkEscapeOrPublicationIsRejected(String kind) throws Exception {
        var inspect = container(false); var settings = (ObjectNode) inspect.path("NetworkSettings");
        var networks = (ObjectNode) settings.path("Networks"); var ports = (ObjectNode) settings.path("Ports");
        switch (kind) {
            case "extra-network" -> networks.putObject("ingress").put("NetworkID", INGRESS);
            case "foreign-network" -> ((ObjectNode) networks.path("services")).put("NetworkID", INGRESS);
            case "empty-network" -> ((ObjectNode) networks.path("services")).remove("NetworkID");
            case "missing-network" -> networks.removeAll();
            case "published-loopback", "published-public" -> ports.putArray("5432/tcp").addObject()
                .put("HostIp", kind.equals("published-loopback") ? "127.0.0.1" : "0.0.0.0").put("HostPort", "15432");
            case "missing-ports" -> settings.remove("Ports");
        }
        var actual = roundtrip(inspect);
        assertThatThrownBy(() -> MaxMediaTestInfrastructure.verifyService(actual, SERVICES)).isInstanceOf(IllegalStateException.class);
    }

    @Test void onlyRelayHasTwoDistinctRandomLoopbackBindings() throws Exception {
        MaxMediaTestInfrastructure.verifyRelay(roundtrip(container(true)), SERVICES, INGRESS);
    }

    @ParameterizedTest @ValueSource(strings = {"internal-only", "extra-network", "empty-ports", "null-binding", "empty-binding", "extra-port", "public-ip", "ipv6-ip", "duplicate-binding", "default-db", "default-amqp", "low-port", "overflow", "not-a-port", "duplicate-port"})
    void docker29MissingMappingAndAmbiguousRelayBindingsFailBeforeProtocolConnections(String kind) throws Exception {
        var inspect = container(true); var settings = (ObjectNode) inspect.path("NetworkSettings");
        var ports = (ObjectNode) settings.path("Ports"); var binding = (ObjectNode) ports.path("5432/tcp").get(0);
        switch (kind) {
            case "internal-only" -> ((ObjectNode) settings.path("Networks")).remove("ingress");
            case "extra-network" -> ((ObjectNode) settings.path("Networks")).putObject("foreign").put("NetworkID", "foreign");
            case "empty-ports" -> ports.removeAll();
            case "null-binding" -> ports.putNull("5432/tcp");
            case "empty-binding" -> ports.putArray("5432/tcp");
            case "extra-port" -> ports.putNull("15672/tcp");
            case "public-ip" -> binding.put("HostIp", "0.0.0.0");
            case "ipv6-ip" -> binding.put("HostIp", "::");
            case "duplicate-binding" -> ((com.fasterxml.jackson.databind.node.ArrayNode) ports.path("5432/tcp")).add(binding.deepCopy());
            case "default-db" -> binding.put("HostPort", "5432");
            case "default-amqp" -> binding.put("HostPort", "5672");
            case "low-port" -> binding.put("HostPort", "80");
            case "overflow" -> binding.put("HostPort", "65536");
            case "not-a-port" -> binding.put("HostPort", "random");
            case "duplicate-port" -> binding.put("HostPort", "15672");
        }
        var actual = roundtrip(inspect);
        assertThatThrownBy(() -> MaxMediaTestInfrastructure.verifyRelay(actual, SERVICES, INGRESS)).isInstanceOf(IllegalStateException.class);
    }

    private ObjectNode network(boolean internal, String role) {
        var n = mapper.createObjectNode().put("Internal", internal).put("Driver", "bridge");
        n.putObject("Labels").put("io.iguana.owner", MaxMediaChainFixture.OWNER).put("io.iguana.test-nonce", NONCE).put("io.iguana.fixture-role", role);
        return n;
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void eachNetworkHasItsExactOwnedRoleAndIsolation(boolean internal) throws Exception {
        String role = internal ? "services" : "ingress";
        MaxMediaTestInfrastructure.verifyNetwork(mapper.valueToTree(mapper.treeToValue(network(internal, role), Network.class)), internal, role, NONCE);
    }

    @ParameterizedTest @ValueSource(strings = {"external-service", "internal-ingress", "owner", "nonce", "role", "driver"})
    void alteredNetworkIdentityOrIsolationIsRejected(String kind) throws Exception {
        boolean internal = !kind.equals("internal-ingress"); String role = internal ? "services" : "ingress";
        var inspect = network(internal, role); var labels = (ObjectNode) inspect.path("Labels");
        switch (kind) {
            case "external-service", "internal-ingress" -> inspect.put("Internal", !internal);
            case "owner" -> labels.put("io.iguana.owner", "foreign");
            case "nonce" -> labels.put("io.iguana.test-nonce", "b".repeat(32));
            case "role" -> labels.put("io.iguana.fixture-role", "foreign");
            case "driver" -> inspect.put("Driver", "host");
        }
        var actual = mapper.valueToTree(mapper.treeToValue(inspect, Network.class));
        assertThatThrownBy(() -> MaxMediaTestInfrastructure.verifyNetwork(actual, internal, role, NONCE)).isInstanceOf(IllegalStateException.class);
    }

    private ObjectNode imageInspection(boolean relay) {
        var inspect = container(relay);
        var exposed = inspect.putObject("Config").putObject("ExposedPorts");
        exposed.putObject("5432/tcp");
        if (relay) exposed.putObject("5672/tcp");
        return inspect;
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void realImagePortPlaceholdersPreserveUnpublishedServiceInspection(boolean emptyArray) throws Exception {
        var inspect = imageInspection(false);
        if (emptyArray) ((ObjectNode) inspect.path("NetworkSettings").path("Ports")).putArray("5432/tcp");
        var actual = roundtrip(inspect);
        assertThat(actual.path("Config").path("ExposedPorts").path("5432/tcp").isObject()).isTrue();
        assertThat(actual.path("Config").path("ExposedPorts").path("5432/tcp").isEmpty()).isTrue();
        MaxMediaTestInfrastructure.verifyService(actual, SERVICES);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void realImagePortPlaceholdersDoNotHidePublishedServicePorts(boolean loopback) throws Exception {
        var inspect = imageInspection(false);
        ((ObjectNode) inspect.path("NetworkSettings").path("Ports")).putArray("5432/tcp").addObject()
            .put("HostIp", loopback ? "127.0.0.1" : "0.0.0.0").put("HostPort", "15432");
        var actual = roundtrip(inspect);
        assertThat(actual.path("NetworkSettings").path("Ports").path("5432/tcp").get(0).path("HostPort").asText()).isEqualTo("15432");
        assertThatThrownBy(() -> MaxMediaTestInfrastructure.verifyService(actual, SERVICES)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void realImagePortPlaceholdersPreserveRelayLoopbackBoundary(boolean publicIp) throws Exception {
        var inspect = imageInspection(true);
        if (publicIp) ((ObjectNode) inspect.path("NetworkSettings").path("Ports").path("5432/tcp").get(0)).put("HostIp", "0.0.0.0");
        var actual = roundtrip(inspect);
        assertThat(actual.path("Config").path("ExposedPorts").size()).isEqualTo(2);
        if (publicIp) assertThatThrownBy(() -> MaxMediaTestInfrastructure.verifyRelay(actual, SERVICES, INGRESS)).isInstanceOf(IllegalStateException.class);
        else MaxMediaTestInfrastructure.verifyRelay(actual, SERVICES, INGRESS);
    }

    @Test void privateServiceConfigurationClearsAnyAccidentalPortPublication() {
        var config = HostConfig.newHostConfig().withPublishAllPorts(true);
        var ports = new Ports(); ports.bind(com.github.dockerjava.api.model.ExposedPort.tcp(5432), Ports.Binding.bindPort(15432)); config.withPortBindings(ports);
        MaxMediaTestInfrastructure.privateHost(config, 192);
        assertThat(config.getPublishAllPorts()).isFalse(); assertThat(config.getPortBindings().getBindings()).isEmpty();
        assertThat(config.getMemory()).isEqualTo(192L * 1024 * 1024);
    }
}
