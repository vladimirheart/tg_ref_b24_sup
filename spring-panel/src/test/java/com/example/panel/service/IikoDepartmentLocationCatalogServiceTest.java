package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IikoDepartmentLocationCatalogServiceTest {

    @Test
    void buildCatalogFromDepartmentNamesParsesBusinessTypeCityAndLocation() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        IikoDepartmentLocationCatalogService service = new IikoDepartmentLocationCatalogService(
                new LocationsIikoServerSourceSettingsService(),
                new SharedConfigService(objectMapper, Files.createTempDirectory("shared-config").toString()),
                objectMapper,
                new UnsupportedGateway()
        );

        Map<String, Object> fallbackTree = Map.of(
                "БлинБери", Map.of(
                        "Корпоративная сеть", Map.of("Москва", List.of("Тверская")),
                        "Партнёры-франчайзи", Map.of("Йошкар-Ола", List.of("Баумана"))
                ),
                "СушиВёсла", Map.of(
                        "Партнёры-франчайзи", Map.of("Ростов-на-Дону", List.of("Зорге 33"))
                )
        );

        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot snapshot = service.buildCatalogFromDepartmentNames(
                List.of(
                        "ФР_ББ Йошкар-Ола Баумана",
                        "ББ Москва Вегас",
                        "ФР_СВ Ростов-на-Дону Зорге 33",
                        "CLOSED_ФР_ББ Москва Тверская"
                ),
                fallbackTree
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> blinberi = (Map<String, Object>) snapshot.tree().get("БлинБери");
        @SuppressWarnings("unchecked")
        Map<String, Object> corporate = (Map<String, Object>) blinberi.get("Корпоративная сеть");
        @SuppressWarnings("unchecked")
        Map<String, Object> franchise = (Map<String, Object>) blinberi.get("Партнёры-франчайзи");
        @SuppressWarnings("unchecked")
        Map<String, Object> sushiVesla = (Map<String, Object>) snapshot.tree().get("СушиВёсла");
        @SuppressWarnings("unchecked")
        Map<String, Object> sushiFranchise = (Map<String, Object>) sushiVesla.get("Партнёры-франчайзи");

        assertThat(snapshot.source()).isEqualTo("iiko_api");
        assertThat((List<String>) corporate.get("Москва")).containsExactlyInAnyOrder("Тверская", "Вегас");
        assertThat((List<String>) franchise.get("Йошкар-Ола")).containsExactly("Баумана");
        assertThat((List<String>) sushiFranchise.get("Ростов-на-Дону")).containsExactly("Зорге 33");
        assertThat(snapshot.tree().toString()).doesNotContain("CLOSED");
        assertThat(snapshot.statuses())
                .containsEntry("location::БлинБери::Корпоративная сеть::Москва::Тверская", "Закрыт")
                .containsEntry("location::БлинБери::Корпоративная сеть::Москва::Вегас", "Активен")
                .containsEntry("location::БлинБери::Партнёры-франчайзи::Йошкар-Ола::Баумана", "Активен");
    }

    @Test
    void buildCatalogFromDepartmentNamesKeepsMissingFallbackLocationsAsClosed() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        IikoDepartmentLocationCatalogService service = new IikoDepartmentLocationCatalogService(
                new LocationsIikoServerSourceSettingsService(),
                new SharedConfigService(objectMapper, Files.createTempDirectory("shared-config").toString()),
                objectMapper,
                new UnsupportedGateway()
        );

        Map<String, Object> fallbackTree = Map.of(
                "БлинБери", Map.of(
                        "Корпоративная сеть", Map.of(
                                "Москва", List.of("Тверская", "Вегас")
                        )
                )
        );

        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot snapshot = service.buildCatalogFromDepartmentNames(
                List.of("ББ Москва Вегас"),
                fallbackTree,
                Map.of("location::БлинБери::Корпоративная сеть::Москва::Тверская", "Активен")
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> blinberi = (Map<String, Object>) snapshot.tree().get("БлинБери");
        @SuppressWarnings("unchecked")
        Map<String, Object> corporate = (Map<String, Object>) blinberi.get("Корпоративная сеть");

        assertThat((List<String>) corporate.get("Москва")).containsExactlyInAnyOrder("Тверская", "Вегас");
        assertThat(snapshot.statuses())
                .containsEntry("location::БлинБери::Корпоративная сеть::Москва::Тверская", "Закрыт")
                .containsEntry("location::БлинБери::Корпоративная сеть::Москва::Вегас", "Активен");
    }

    @Test
    void buildEffectiveLocationsPayloadGeneratesMetaForLiveCatalogAndPreservesStatuses() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SharedConfigService sharedConfigService = new SharedConfigService(
                objectMapper,
                Files.createTempDirectory("shared-config").toString()
        );
        sharedConfigService.saveLocations(Map.of(
                "tree", Map.of("БлинБери", Map.of("Корпоративная сеть", Map.of("Москва", List.of("Тверская")))),
                "statuses", Map.of("open", "Открыта")
        ));
        IikoDepartmentLocationCatalogService service = new IikoDepartmentLocationCatalogService(
                new LocationsIikoServerSourceSettingsService(),
                sharedConfigService,
                objectMapper,
                new UnsupportedGateway()
        );

        Map<String, Object> payload = service.buildEffectiveLocationsPayload(
                new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(
                        Map.of("БлинБери", Map.of("Корпоративная сеть", Map.of("Смоленск", List.of("Ленина 1")))),
                        Map.of(),
                        "iiko_api",
                        false,
                        List.of()
                )
        );

        assertThat(payload).containsEntry("statuses", Map.of("open", "Открыта"));
        assertThat(payload).containsKey("city_meta");
        assertThat(payload).containsKey("location_meta");
        assertThat(payload.get("city_meta").toString()).contains("Смоленск");
        assertThat(payload.get("location_meta").toString()).contains("Ленина 1");
    }

    @Test
    void loadCatalogUsesOnlyConfiguredEnabledIikoServerSources() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SharedConfigService sharedConfigService = new SharedConfigService(
                objectMapper,
                Files.createTempDirectory("shared-config").toString()
        );
        sharedConfigService.saveLocations(Map.of(
                "tree", Map.of("БлинБери", Map.of("Корпоративная сеть", Map.of("Москва", List.of("Тестовая")))),
                "statuses", Map.of()
        ));
        sharedConfigService.saveSettings(Map.of(
                LocationsIikoServerSourceSettingsService.SETTINGS_KEY,
                List.of(
                        Map.of(
                                "id", "source-a",
                                "name", "Server A",
                                "base_url", "https://server-a.example/",
                                "api_login", "login-a",
                                "api_secret", "secret-a",
                                "enabled", true
                        ),
                        Map.of(
                                "id", "source-b",
                                "name", "Server B",
                                "base_url", "https://server-b.example",
                                "api_login", "login-b",
                                "api_secret", "secret-b",
                                "enabled", false
                        )
                )
        ));

        IikoDepartmentLocationCatalogService service = new IikoDepartmentLocationCatalogService(
                new LocationsIikoServerSourceSettingsService(),
                sharedConfigService,
                objectMapper,
                new IikoDepartmentLocationCatalogService.IikoDepartmentGateway() {
                    @Override
                    public String requestAccessToken(String baseUrl, String apiLogin, String apiSecret) {
                        assertThat(baseUrl).isEqualTo("https://server-a.example");
                        assertThat(apiLogin).isEqualTo("login-a");
                        assertThat(apiSecret).isEqualTo("secret-a");
                        return "token";
                    }

                    @Override
                    public List<String> loadActiveDepartmentNames(String baseUrl, String token) {
                        assertThat(baseUrl).isEqualTo("https://server-a.example");
                        assertThat(token).isEqualTo("token");
                        return List.of("ББ Смоленск Ленина 1", "CLOSED ББ Смоленск Архив");
                    }
                }
        );

        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot snapshot = service.loadCatalog();

        assertThat(snapshot.source()).isEqualTo("iiko_api");
        assertThat(snapshot.tree().toString()).contains("Смоленск");
        assertThat(snapshot.tree().toString()).contains("Ленина 1");
        assertThat(snapshot.tree().toString()).doesNotContain("CLOSED");
        assertThat(snapshot.warnings()).isEmpty();
    }

    @Test
    void httpGatewayUsesProvidedSha1PasswordAsIs() throws Exception {
        AtomicInteger authCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/resto/api/auth", exchange -> {
            authCalls.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/resto/api/auth");
            assertThat(exchange.getRequestURI().getRawQuery()).contains("login=test-login");
            assertThat(exchange.getRequestURI().getRawQuery()).contains("pass=5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8");
            byte[] body = "token-123".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/resto/api/corporation/departments/", exchange -> {
            assertThat(exchange.getRequestURI().getRawQuery()).contains("key=token-123");
            byte[] body = """
                    <root>
                      <corporateItemDto>
                        <type>DEPARTMENT</type>
                        <isActive>true</isActive>
                        <name>ББ Смоленск Ленина 1</name>
                      </corporateItemDto>
                    </root>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            IikoDepartmentLocationCatalogService.HttpIikoDepartmentGateway gateway =
                    new IikoDepartmentLocationCatalogService.HttpIikoDepartmentGateway(new ObjectMapper());

            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            String token = gateway.requestAccessToken(baseUrl, "test-login", "5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8");
            List<String> departments = gateway.loadActiveDepartmentNames(baseUrl, token);

            assertThat(token).isEqualTo("token-123");
            assertThat(departments).containsExactly("ББ Смоленск Ленина 1");
            assertThat(authCalls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpGatewayLoadsOnlySupportedDepartmentTypes() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/resto/api/corporation/departments/", exchange -> {
            assertThat(exchange.getRequestURI().getRawQuery()).contains("key=token-123");
            byte[] body = """
                    <root>
                      <corporateItemDto>
                        <type>DEPARTMENT</type>
                        <isActive>true</isActive>
                        <name>BB Smolensk Department</name>
                      </corporateItemDto>
                      <corporateItemDto>
                        <type>GROUP</type>
                        <isActive>true</isActive>
                        <name>BB Smolensk Group</name>
                      </corporateItemDto>
                      <corporateItemDto>
                        <type>MANUFACTURE</type>
                        <isActive>true</isActive>
                        <name>BB Smolensk Manufacture</name>
                      </corporateItemDto>
                      <corporateItemDto>
                        <type>CENTRALSTORE</type>
                        <isActive>true</isActive>
                        <name>BB Smolensk Central Store</name>
                      </corporateItemDto>
                    </root>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            IikoDepartmentLocationCatalogService.HttpIikoDepartmentGateway gateway =
                    new IikoDepartmentLocationCatalogService.HttpIikoDepartmentGateway(new ObjectMapper());

            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

            assertThat(gateway.loadActiveDepartmentNames(baseUrl, "token-123")).containsExactly(
                    "BB Smolensk Department",
                    "BB Smolensk Manufacture",
                    "BB Smolensk Central Store");
        } finally {
            server.stop(0);
        }
    }

    private static final class UnsupportedGateway implements IikoDepartmentLocationCatalogService.IikoDepartmentGateway {

        @Override
        public String requestAccessToken(String baseUrl, String apiLogin, String apiSecret) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> loadActiveDepartmentNames(String baseUrl, String token) {
            throw new UnsupportedOperationException();
        }
    }
}
