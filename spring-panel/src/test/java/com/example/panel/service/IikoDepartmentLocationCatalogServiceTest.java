package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.repository.IikoApiMonitorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IikoDepartmentLocationCatalogServiceTest {

    @Test
    void buildCatalogFromDepartmentNamesParsesBusinessTypeCityAndLocation() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        IikoDepartmentLocationCatalogService service = new IikoDepartmentLocationCatalogService(
                new IikoApiMonitorRepository(null),
                new SharedConfigService(objectMapper, Files.createTempDirectory("shared-config").toString()),
                objectMapper,
                new IikoDepartmentLocationCatalogService.IikoDepartmentGateway() {
                    @Override
                    public String requestAccessToken(String baseUrl, String apiLogin) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public List<String> loadOrganizationIds(String baseUrl, String token) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public List<String> loadActiveDepartmentNames(String baseUrl, String token, List<String> organizationIds) {
                        throw new UnsupportedOperationException();
                    }
                }
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
        assertThat((List<String>) corporate.get("Москва")).containsExactly("Вегас");
        assertThat((List<String>) franchise.get("Йошкар-Ола")).containsExactly("Баумана");
        assertThat((List<String>) sushiFranchise.get("Ростов-на-Дону")).containsExactly("Зорге 33");
        assertThat(snapshot.tree().toString()).doesNotContain("CLOSED");
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
                new IikoApiMonitorRepository(null),
                sharedConfigService,
                objectMapper,
                new IikoDepartmentLocationCatalogService.IikoDepartmentGateway() {
                    @Override
                    public String requestAccessToken(String baseUrl, String apiLogin) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public List<String> loadOrganizationIds(String baseUrl, String token) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public List<String> loadActiveDepartmentNames(String baseUrl, String token, List<String> organizationIds) {
                        throw new UnsupportedOperationException();
                    }
                }
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
}
