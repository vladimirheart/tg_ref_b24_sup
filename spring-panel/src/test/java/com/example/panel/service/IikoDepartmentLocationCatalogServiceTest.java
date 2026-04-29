package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.entity.IikoApiMonitor;
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
                    public List<String> loadActiveOrganizationNames(String baseUrl, String token) {
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
                    public List<String> loadActiveOrganizationNames(String baseUrl, String token) {
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

    @Test
    void loadCatalogUsesOnlyOrganizationMonitorsFlaggedForLocationSync() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SharedConfigService sharedConfigService = new SharedConfigService(
                objectMapper,
                Files.createTempDirectory("shared-config").toString()
        );
        sharedConfigService.saveLocations(Map.of(
                "tree", Map.of("Р‘Р»РёРЅР‘РµСЂРё", Map.of("РљРѕСЂРїРѕСЂР°С‚РёРІРЅР°СЏ СЃРµС‚СЊ", Map.of("РЎРјРѕР»РµРЅСЃРє", List.of("Тестовая")))),
                "statuses", Map.of()
        ));

        IikoApiMonitor sourceMonitor = new IikoApiMonitor();
        sourceMonitor.setEnabled(true);
        sourceMonitor.setLocationsSyncEnabled(true);
        sourceMonitor.setRequestType("organizations");
        sourceMonitor.setBaseUrl("https://api-ru.iiko.services");
        sourceMonitor.setApiLogin("key-1");

        IikoApiMonitor ignoredMonitor = new IikoApiMonitor();
        ignoredMonitor.setEnabled(true);
        ignoredMonitor.setLocationsSyncEnabled(false);
        ignoredMonitor.setRequestType("organizations");
        ignoredMonitor.setBaseUrl("https://ignored.example");
        ignoredMonitor.setApiLogin("key-2");

        IikoApiMonitorRepository monitorRepository = new IikoApiMonitorRepository(null) {
            @Override
            public List<IikoApiMonitor> findAllByOrderByMonitorNameAscIdAsc() {
                return List.of(sourceMonitor, ignoredMonitor);
            }
        };

        IikoDepartmentLocationCatalogService service = new IikoDepartmentLocationCatalogService(
                monitorRepository,
                sharedConfigService,
                objectMapper,
                new IikoDepartmentLocationCatalogService.IikoDepartmentGateway() {
                    @Override
                    public String requestAccessToken(String baseUrl, String apiLogin) {
                        assertThat(baseUrl).isEqualTo("https://api-ru.iiko.services");
                        assertThat(apiLogin).isEqualTo("key-1");
                        return "token";
                    }

                    @Override
                    public List<String> loadActiveOrganizationNames(String baseUrl, String token) {
                        assertThat(baseUrl).isEqualTo("https://api-ru.iiko.services");
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
}
