package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsOverviewOrderUiSourceContractTest {

    private static final Path SETTINGS = Path.of("src/main/resources/templates/settings/index.html");
    private static final Pattern TARGET = Pattern.compile("data-settings-overview-target=\"([^\"]+)\"");
    private static final List<String> EXPECTED_ORDER = List.of(
        "channelsModal",
        "reportingModal",
        "inputFormattingModal",
        "categoriesModal",
        "panelDesignSettingsModal",
        "parametersModal",
        "itConnectionsModal",
        "usersModal",
        "managerBindingsModal",
        "locationsModal",
        "legalEntitiesModal",
        "backupSettingsModal",
        "productionReadinessModal",
        "storageInventoryModal"
    );

    @Test
    void overviewTilesKeepRussianAlphabeticalOrderAndPermissionGuards() throws IOException {
        String html = read(SETTINGS);
        int gridStart = html.indexOf("settings-tiles");
        int modalStart = html.indexOf("<div class=\"modal fade", gridStart);
        assertThat(gridStart).isGreaterThanOrEqualTo(0);
        assertThat(modalStart).isGreaterThan(gridStart);

        String overview = html.substring(gridStart, modalStart);
        Matcher matcher = TARGET.matcher(overview);
        List<String> actual = new ArrayList<>();
        while (matcher.find()) {
            actual.add(matcher.group(1));
        }

        assertThat(actual).containsExactlyElementsOf(EXPECTED_ORDER);
        assertThat(overview)
            .contains("th:if=\"${canManageChannels}\"")
            .contains("th:if=\"${canManageUsers}\"")
            .contains("th:if=\"${canRunStorageInventory}\"")
            .contains("<h5 class=\"card-title\">Настройка вводимых данных</h5>")
            .contains("<h5 class=\"card-title\">Юридические лица</h5>")
            .contains("<h5 class=\"card-title\">Backup &amp; recovery</h5>");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}
