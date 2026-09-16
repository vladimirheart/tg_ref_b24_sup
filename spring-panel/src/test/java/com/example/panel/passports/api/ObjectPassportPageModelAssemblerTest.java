package com.example.panel.passports.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.repository.ItEquipmentCatalogRepository;
import com.example.panel.service.IikoDepartmentLocationCatalogService;
import com.example.panel.service.SettingsCatalogService;
import com.example.panel.service.SettingsParameterService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

class ObjectPassportPageModelAssemblerTest {

    @Test
    void populatesEditorModelFromEffectiveLocationCatalog() {
        ItEquipmentCatalogRepository equipmentRepository = mock(ItEquipmentCatalogRepository.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        SettingsCatalogService settingsCatalogService = mock(SettingsCatalogService.class);
        SettingsParameterService settingsParameterService = mock(SettingsParameterService.class);
        IikoDepartmentLocationCatalogService locationCatalogService = mock(IikoDepartmentLocationCatalogService.class);

        Map<String, String> parameterTypes = new LinkedHashMap<>();
        parameterTypes.put("business", "Бизнес");
        parameterTypes.put("partner_type", "Тип партнёра");
        parameterTypes.put("country", "Страна");
        parameterTypes.put("city", "Город");
        parameterTypes.put("department", "Департамент");
        parameterTypes.put("legal_entity", "ЮЛ");
        when(settingsCatalogService.getParameterTypes()).thenReturn(parameterTypes);
        when(settingsCatalogService.getParameterDependencies()).thenReturn(Map.of());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(settingsParameterService.listParameters(false)).thenReturn(Map.of());
        when(equipmentRepository.findAll()).thenReturn(List.of());

        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot liveCatalog =
                new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(
                        Map.of("БлинБери", Map.of("Корпоративная сеть", Map.of("Смоленск", List.of("Ленина 1")))),
                        Map.of(),
                        "iiko_api",
                        false,
                        List.of()
                );
        Map<String, Object> effectiveLocationsPayload = Map.of(
                "tree", liveCatalog.tree(),
                "statuses", Map.of(),
                "city_meta", Map.of(
                        "БлинБери::Корпоративная сеть::Смоленск",
                        Map.of("country", "Россия", "partner_type", "Корпоративная сеть")),
                "location_meta", Map.of(
                        "БлинБери::Корпоративная сеть::Смоленск::Ленина 1",
                        Map.of("country", "Россия", "partner_type", "Корпоративная сеть"))
        );
        when(locationCatalogService.loadCatalog()).thenReturn(liveCatalog);
        when(locationCatalogService.buildEffectiveLocationsPayload(liveCatalog)).thenReturn(effectiveLocationsPayload);
        when(settingsCatalogService.collectCities(liveCatalog.tree())).thenReturn(List.of("Смоленск"));

        ObjectPassportPageModelAssembler assembler = new ObjectPassportPageModelAssembler(
                equipmentRepository,
                sharedConfigService,
                settingsCatalogService,
                settingsParameterService,
                locationCatalogService,
                new ObjectMapper());
        ExtendedModelMap model = new ExtendedModelMap();

        assembler.populatePassportEditor(model, true);

        assertThat(((List<?>) model.get("statuses")).contains("Удалён")).isTrue();
        assertThat(model.get("cities")).isEqualTo(List.of("Смоленск"));
        assertThat(model.get("passportPayloadJson")).isEqualTo("{\"is_new\":true}");
        assertThat(model.get("itEquipmentCatalog")).isEqualTo(List.of());
        assertThat(((Map<?, ?>) model.get("parameterValues")).get("department"))
                .isEqualTo(List.of("Ленина 1"));
    }
}
