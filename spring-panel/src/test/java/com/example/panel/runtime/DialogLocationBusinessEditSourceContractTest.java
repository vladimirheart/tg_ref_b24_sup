package com.example.panel.runtime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DialogLocationBusinessEditSourceContractTest {

    @Test
    void operatorCanEditDialogBusinessAndLocationThroughDedicatedApiAndCatalogUi() throws IOException {
        String controller = Files.readString(
                Path.of("src/main/java/com/example/panel/controller/DialogLocationBusinessApiController.java"), UTF_8);
        String template = Files.readString(Path.of("src/main/resources/templates/dialogs/index.html"), UTF_8);
        String runtime = Files.readString(
                Path.of("src/main/resources/static/js/dialogs-location-business-runtime.js"), UTF_8);

        assertThat(controller)
                .contains("@GetMapping(\"/location-business/catalog\")")
                .contains("@PatchMapping(\"/{ticketId}/location-business\")")
                .contains("\"can_assign\"")
                .contains("IikoDepartmentLocationCatalogService")
                .contains("UPDATE messages")
                .contains("business = ?")
                .contains("location_type = ?")
                .contains("city = ?")
                .contains("location_name = ?")
                .contains("updated_at = CURRENT_TIMESTAMP")
                .contains("updated_by = ?")
                .contains("WHERE ticket_id = ?")
                .contains("dialog_metadata_updated");

        assertThat(template)
                .contains("id=\"dialogDetailsLocationBusinessBtn\"")
                .contains("id=\"dialogLocationBusinessModal\"")
                .contains("id=\"dialogLocationBusinessBusiness\"")
                .contains("id=\"dialogLocationBusinessLocation\"")
                .contains("dialogs-location-business-runtime.js(v='20261002-01-278-s2-r7')");

        assertThat(runtime)
                .contains("fetch('/api/dialogs/location-business/catalog'")
                .contains("/location-business")
                .contains("requestOptions('PATCH'")
                .contains("window.location.reload();")
                .contains("locationType:")
                .contains("locationName:");
    }
}
