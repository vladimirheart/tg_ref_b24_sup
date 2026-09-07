package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientProfileCompactUiSourceContractTest {
    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void clientProfileIsCompactAndUsesRequestNumbersWithRicherHistoryPreview() throws IOException {
        String template = read("spring-panel/src/main/resources/templates/clients/profile.html");
        String scss = read("spring-panel/src/main/resources/scss/app/_unified-ui.scss");
        String ticketModel = read("spring-panel/src/main/java/com/example/panel/model/clients/ClientProfileTicket.java");
        String clientsService = read("spring-panel/src/main/java/com/example/panel/service/ClientsService.java");
        String taskList = read("ai-context/tasks/task-list.md");

        assertThat(template)
            .contains("client-profile-page-header-row")
            .contains("id=\"clientPhonePrimary\"")
            .contains("id=\"clientPhonesModal\"")
            .contains("id=\"usernameHistoryModal\"")
            .contains("📋 Обращения клиента")
            .contains("ticket.requestNumber")
            .contains("client-ticket-location")
            .contains("id=\"clientHistoryOpenDialog\"")
            .contains("Открыть полный диалог")
            .contains("function renderClientHistoryAttachment(msg)")
            .contains("chat-message-row")
            .contains("@{/css/app.css(v='")
            .doesNotContain("📋 Заявки клиента")
            .doesNotContain("<h5 class=\"card-title\">📞 Телефоны клиента</h5>")
            .doesNotContain("<h5 class=\"card-title\">📝 История юзернейма</h5>")
            .doesNotContain("ID заявки:");

        assertThat(ticketModel).contains("String requestNumber,");
        assertThat(clientsService).contains("m.group_msg_id AS request_number").contains("rs.getString(\"request_number\")");
        assertThat(scss).contains("/* 01-256: compact client profile overview and dialog history preview */").contains(".client-profile-page-header-row").contains(".client-profile-primary-shell").contains(".client-ticket-summary").contains("#clientHistoryModal .client-profile-chat-history");
        assertThat(taskList).contains("🟢 [01-255] Уплотнить идентификационную шапку диалога и вынести бизнес в центр").contains("🟣 [01-256] Уплотнить карточку клиента и улучшить список/историю обращений");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
