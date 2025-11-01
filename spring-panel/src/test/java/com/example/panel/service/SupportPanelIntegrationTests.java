package com.example.panel.service;

import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.model.knowledge.KnowledgeArticleCommand;
import com.example.panel.model.knowledge.KnowledgeArticleDetails;
import com.example.panel.model.knowledge.KnowledgeArticleSummary;
import com.example.panel.model.notification.NotificationDto;
import com.example.panel.model.notification.NotificationSummary;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("sqlite")
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration/sqlite"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SupportPanelIntegrationTests {

    private static Path dbFile;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-test", ".db");
        registry.add("app.datasource.sqlite.path", () -> dbFile.toString());
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DialogService dialogService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private PublicFormService publicFormService;

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM web_form_sessions");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM tickets");
        jdbcTemplate.update("DELETE FROM client_statuses");
        jdbcTemplate.update("DELETE FROM channels");
        jdbcTemplate.update("DELETE FROM knowledge_article_files");
        jdbcTemplate.update("DELETE FROM knowledge_articles");
        jdbcTemplate.update("DELETE FROM notifications");
    }

    @Test
    void dialogServiceAggregatesStatsAndHistory() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at) VALUES (1, 'token', 'Demo', 1, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO tickets (user_id, ticket_id, status, channel_id) VALUES (?,?,?,?)",
                1001L, "T-1", "pending", 1);
        jdbcTemplate.update("INSERT INTO messages (group_msg_id, user_id, business, city, location_name, problem, created_at, username, ticket_id, created_date, created_time, client_name, client_status, updated_at, updated_by, channel_id) " +
                        "VALUES (NULL, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, DATE('now'), TIME('now'), ?, ?, CURRENT_TIMESTAMP, 'tester', ?)",
                1001L, "Food", "Москва", "Пиццерия", "Не работает терминал", "ivan", "T-1", "Иван", "VIP", 1);
        jdbcTemplate.update("INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id, message_type, channel_id) VALUES (?,?,?,?,?,?,?)",
                1001L, "user", "Добрый день", OffsetDateTime.now().toString(), "T-1", "text", 1);

        DialogSummary summary = dialogService.loadSummary();
        assertThat(summary.totalTickets()).isEqualTo(1);
        assertThat(summary.pendingTickets()).isEqualTo(1);
        assertThat(summary.channelStats()).extracting("name").contains("Demo");

        DialogDetails details = dialogService.loadDialogDetails("T-1", 1L).orElseThrow();
        assertThat(details.summary().clientName()).isEqualTo("Иван");
        assertThat(details.history()).hasSize(1);
    }

    @Test
    void knowledgeBaseServiceSavesAndListsArticles() {
        KnowledgeArticleCommand command = new KnowledgeArticleCommand(null, "Инструкция", "Поддержка",
                "guide", "draft", "Оператор", "IT", "Сеть", "Кратко", "Подробное описание");
        KnowledgeArticleDetails saved = knowledgeBaseService.saveArticle(command);
        assertThat(saved.id()).isNotNull();

        List<KnowledgeArticleSummary> summaries = knowledgeBaseService.listArticles();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().title()).isEqualTo("Инструкция");
    }

    @Test
    void publicFormServiceCreatesSessionsAndHistory() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id) VALUES (2, 'web', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-demo')");
        PublicFormSubmission submission = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", Map.of());
        PublicFormSessionDto session = publicFormService.createSession("web-demo", submission);
        assertThat(session.token()).isNotBlank();
        assertThat(session.ticketId()).startsWith("web-");

        PublicFormSessionDto loaded = publicFormService.findSession("web-demo", session.token()).orElseThrow();
        assertThat(loaded.clientName()).isEqualTo("Анна");

        assertThat(dialogService.loadHistory(session.ticketId(), null)).isNotEmpty();
    }

    @Test
    void notificationServiceCountsAndMarksAsRead() {
        jdbcTemplate.update("INSERT INTO notifications (user_identity, text, url, is_read, created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)",
                "operator", "Новое сообщение", "/tickets/T-1", 0);
        jdbcTemplate.update("INSERT INTO notifications (user_identity, text, url, is_read, created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)",
                "operator", "Резерв", "/tickets/T-2", 0);

        NotificationSummary summary = notificationService.summary("operator");
        assertThat(summary.unreadCount()).isEqualTo(2);

        List<NotificationDto> notifications = notificationService.findForUser("operator");
        assertThat(notifications).hasSize(2);

        notificationService.markAsRead("operator", notifications.getFirst().id());
        NotificationSummary after = notificationService.summary("operator");
        assertThat(after.unreadCount()).isEqualTo(1);
    }
}
