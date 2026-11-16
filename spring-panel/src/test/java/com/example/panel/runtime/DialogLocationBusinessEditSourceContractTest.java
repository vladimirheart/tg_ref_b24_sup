package com.example.panel.runtime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DialogLocationBusinessEditSourceContractTest {

    @Test
    void legacyLocationApiCannotAssignBusinessAndDialogUiUsesScopedSelection() throws IOException {
        String controller = Files.readString(
                Path.of("src/main/java/com/example/panel/controller/DialogLocationBusinessApiController.java"), UTF_8);
        String template = Files.readString(Path.of("src/main/resources/templates/dialogs/index.html"), UTF_8);
        String runtime = Files.readString(
                Path.of("src/main/resources/static/js/dialogs-business-selection-runtime.js"), UTF_8);

        assertThat(controller)
                .contains("@GetMapping(\"/api/dialogs/location-business/catalog\")")
                .contains("@PatchMapping(\"/api/dialogs/{ticketId}/location-business\")")
                .contains("@PatchMapping(\"/api/dialog-ticket/location-business\")")
                .contains("\"can_assign\"")
                .contains("IikoDepartmentLocationCatalogService")
                .contains("UPDATE messages")
                .contains("location_type = ?")
                .contains("city = ?")
                .contains("location_name = ?")
                .contains("updated_at = CURRENT_TIMESTAMP")
                .contains("updated_by = ?")
                .contains("WHERE ticket_id = ?")
                .contains("dialog_metadata_updated")
                .contains("Бизнес выбирается вопросником или через карточку нераспределённого обращения")
                .doesNotContain("business = ?");

        assertThat(template)
                .contains("id=\"dialogDetailsLocationBusinessBtn\"")
                .contains("id=\"dialogLocationBusinessModal\"")
                .contains("id=\"dialogLocationBusinessBusiness\"")
                .contains("dialogs-business-selection-runtime.js(v='20261116-01-293-r1')")
                .doesNotContain("dialogs-location-business-runtime.js");

        assertThat(runtime)
                .contains("fetch('/api/unresolved-scope/businesses'")
                .contains("/api/unresolved-scope/tickets/")
                .contains("requestOptions('POST'")
                .contains("scopeKind !== 'UNRESOLVED'")
                .contains("window.location.assign(managementUrl);");
    }
}
