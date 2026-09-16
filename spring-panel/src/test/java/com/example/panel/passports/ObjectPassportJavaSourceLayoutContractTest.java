package com.example.panel.passports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ObjectPassportJavaSourceLayoutContractTest {

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void passportCoreServiceUsesFeatureOwnedPackage() throws IOException {
        Path oldService = Path.of("src/main/java/com/example/panel/service/ObjectPassportService.java");
        Path newService = Path.of("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String api = read("src/main/java/com/example/panel/controller/ObjectPassportApiController.java");
        String page = read("src/main/java/com/example/panel/passports/ObjectPassportPageController.java");
        String settingsEquipment = read("src/main/java/com/example/panel/service/SettingsItEquipmentService.java");
        String netBoxSync = read("src/main/java/com/example/panel/service/NetBoxObjectPassportSyncService.java");

        assertThat(Files.exists(oldService)).isFalse();
        assertThat(Files.exists(newService)).isTrue();
        assertThat(service)
                .contains("package com.example.panel.passports;")
                .contains("public class ObjectPassportService")
                .contains("createPassport(")
                .contains("updatePassport(")
                .contains("uploadPhoto(");
        assertThat(api)
                .contains("import com.example.panel.passports.ObjectPassportService;")
                .doesNotContain("import com.example.panel.service.ObjectPassportService;");
        assertThat(page)
                .doesNotContain("import com.example.panel.service.ObjectPassportService;")
                .doesNotContain("import com.example.panel.passports.ObjectPassportService;");
        assertThat(settingsEquipment).contains("import com.example.panel.passports.ObjectPassportService;");
        assertThat(netBoxSync).contains("import com.example.panel.passports.ObjectPassportService;");
    }
}
