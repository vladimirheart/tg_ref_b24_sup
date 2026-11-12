package com.example.panel.passports;

import com.example.panel.passports.ObjectPassportDocumentService.Input;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.ObjectStorageProperties;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ObjectPassportDocumentPersistenceTest {
    @TempDir Path temporary;
    JdbcTemplate jdbc;
    ObjectPassportDocumentRepository repository;
    ObjectPassportDocumentStorage storage;
    ObjectPassportDocumentService service;

    @BeforeEach void setup() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("passport_documents");
        jdbc.execute("CREATE TABLE object_passports(id BIGINT PRIMARY KEY,details TEXT NOT NULL DEFAULT '{}')");
        jdbc.update("INSERT INTO object_passports(id) VALUES (1),(2)");
        var migration = new ResourceDatabasePopulator(new ClassPathResource("db/migration/postgresql/V92__object_passport_documents.sql"));
        migration.setSqlScriptEncoding("UTF-8"); migration.execute(jdbc.getDataSource());
        repository = spy(new ObjectPassportDocumentRepository(jdbc, new ObjectMapper()));
        storage = spy(new ObjectPassportDocumentStorage(new AttachmentObjectStorageService(new ObjectStorageProperties(),
                temporary.resolve("files").toString(), temporary.resolve("kb").toString(), temporary.resolve("photos").toString(), temporary.resolve("avatars").toString())));
        service = new ObjectPassportDocumentService(repository, storage, new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    Input input(int revision, String title) {
        return new Input(revision, title, "Акт приёмки", LocalDate.of(2026, 11, 12), "Касса настроена\nПроверка пройдена", Map.of("Серийный номер", "ККТ-001", "Принял", "Инженер"));
    }
    MockMultipartFile file(String text) { return new MockMultipartFile("file", "акт.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8)); }
    @SuppressWarnings("unchecked") Map<String,Object> document(Map<String,Object> response) { return (Map<String,Object>) response.get("document"); }
    UUID id(Map<String,Object> response) { return UUID.fromString(document(response).get("id").toString()); }
    byte[] download(UUID id, int revision) throws Exception {
        try (var input = service.download(1, id, revision, false).getBody().getInputStream()) { return input.readAllBytes(); }
    }

    @Test void versionsPreserveFilesMetadataAndActorOutsideThePassportJson() throws Exception {
        UUID id = id(service.create(1, input(0, "Приёмка"), file("Первый файл"), "Инженер"));
        service.revise(1, id, input(1, "Приёмка после проверки"), file("Исправленный файл"), "Начальник");
        assertThat(new String(download(id, 1), StandardCharsets.UTF_8)).isEqualTo("Первый файл");
        assertThat(new String(download(id, 2), StandardCharsets.UTF_8)).isEqualTo("Исправленный файл");
        assertThat(repository.version(1, id, 1).title()).isEqualTo("Приёмка");
        assertThat(repository.version(1, id, 2).actor()).isEqualTo("Начальник");
        jdbc.update("UPDATE object_passports SET details=? WHERE id=1", "{\"department\":\"Новое имя\"}");
        assertThat(repository.current(1, id, false).revision()).isEqualTo(2);
        var payload = document(service.version(1, id, 1));
        assertThat(payload.toString()).doesNotContain("storage_key", "passport_documents/");
        assertThat(payload).containsEntry("fields", input(0, "").fields());
    }

    @Test void editingMetadataKeepsTheFileAndArchivingIsReversible() throws Exception {
        UUID id = id(service.create(1, input(0, "Приёмка"), file("Исходный"), "Инженер"));
        service.revise(1, id, input(1, "Дополнено"), null, "Инженер");
        assertThat(download(id, 2)).isEqualTo(download(id, 1));
        service.archive(1, id, 2, true, "Инженер");
        assertThat(repository.count(1, false, "")).isZero(); assertThat(repository.count(1, true, "")).isEqualTo(1);
        assertThatThrownBy(() -> service.revise(1, id, input(3, "Нельзя"), null, "Инженер")).isInstanceOf(ResponseStatusException.class);
        service.archive(1, id, 3, false, "Инженер");
        assertThat(repository.history(1, id, Integer.MAX_VALUE, 30)).extracting(ObjectPassportDocument::revision).containsExactly(4,3,2,1);
        assertThat(repository.count(1, false, "")).isEqualTo(1);
    }

    @Test void notesWithoutFilesAreEditableAndCannotInventABinaryReference() throws Exception {
        UUID id = id(service.create(1, input(0, "Историческая запись"), null, "Автор"));
        service.revise(1, id, input(1, "Дополнение"), null, "Автор");
        assertThat(repository.current(1, id, false).file()).isNull();
        assertThatThrownBy(() -> service.download(1, id, 1, false)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void aDocumentCannotBeReadOrChangedThroughAnotherPassport() throws Exception {
        UUID id = id(service.create(1, input(0, "Приёмка"), file("Только паспорт 1"), "Автор"));
        for (var action : List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                () -> service.version(2, id, 1), () -> service.history(2, id, null), () -> service.download(2, id, 1, false),
                () -> service.revise(2, id, input(1, "Чужой"), file("Подмена"), "Автор"), () -> service.archive(2, id, 1, true, "Автор"))) {
            assertThatThrownBy(action).isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404);
        }
        assertThat(repository.current(1, id, false).revision()).isEqualTo(1);
    }

    @Test void staleUpdatesFailBeforeStoringANewFile() throws Exception {
        UUID id = id(service.create(1, input(0, "Приёмка"), file("Исходный"), "Автор"));
        service.revise(1, id, input(1, "Новая версия"), null, "Автор");
        clearInvocations(storage);
        assertThatThrownBy(() -> service.revise(1, id, input(1, "Устарело"), file("Лишний файл"), "Автор"))
                .isInstanceOf(ResponseStatusException.class).extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409);
        verify(storage, never()).store(anyLong(), any(), any());
        assertThat(repository.current(1, id, false).revision()).isEqualTo(2);
    }

    @Test void rollbackRemovesOnlyTheNewBinaryAndKeepsCommittedHistory() throws Exception {
        UUID id = id(service.create(1, input(0, "Приёмка"), file("Исходный"), "Автор"));
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Simulated database write failure"); })
                .when(repository).append(any(), eq(1));
        assertThatThrownBy(() -> service.revise(1, id, input(1, "Не сохранено"), file("Новый"), "Автор"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.current(1, id, false).revision()).isEqualTo(1);
        assertThat(repository.history(1, id, Integer.MAX_VALUE, 30)).hasSize(1);
        assertThat(new String(download(id, 1), StandardCharsets.UTF_8)).isEqualTo("Исходный");
        try (var files = Files.walk(temporary.resolve("files"))) { assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(1); }
        verify(storage).discard(anyString());
    }

    @Test void hardDeletingAPassportCannotSilentlyDiscardItsDocuments() throws Exception {
        UUID id = id(service.create(1, input(0, "История"), null, "Автор"));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM object_passports WHERE id=1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(repository.current(1, id, false).title()).isEqualTo("История");
    }

    @Test void competingEditsProduceOneNewRevisionAndOneConflict() throws Exception {
        UUID id = id(service.create(1, input(0, "История"), null, "Автор"));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var attempts = executor.invokeAll(List.of(
                () -> editOutcome(id, "Первый"), () -> editOutcome(id, "Второй")));
            assertThat(List.of(attempts.get(0).get(10, TimeUnit.SECONDS), attempts.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally { executor.shutdownNow(); }
        assertThat(repository.current(1, id, false).revision()).isEqualTo(2);
        assertThat(repository.history(1, id, Integer.MAX_VALUE, 30)).hasSize(2);
    }
    private int editOutcome(UUID id, String title) throws Exception {
        try { service.revise(1, id, input(1, title), null, "Автор"); return 200; }
        catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
    }

    @Test @SuppressWarnings("unchecked") void listsAreBoundedAndSearchAndIndividualEditDenialsAreApplied() throws Exception {
        for (int i = 0; i < 27; i++) service.create(1, input(0, "Запись " + i), null, "Автор");
        var actor = new UsernamePasswordAuthenticationToken("reader", "", AuthorityUtils.createAuthorityList("PAGE_OBJECT_PASSPORTS", "DENY_EDIT_PASSPORT_DOCUMENTS"));
        var first = service.list(1, false, "", null, actor);
        assertThat((List<?>) first.get("items")).hasSize(25); assertThat(first).containsEntry("has_more", true).containsEntry("can_edit", false);
        var second = service.list(1, false, "", UUID.fromString(first.get("next_cursor").toString()), actor);
        assertThat((List<?>) second.get("items")).hasSize(2); assertThat(second).containsEntry("has_more", false);
        var firstIds = ((List<Map<String,Object>>) first.get("items")).stream().map(row -> row.get("id")).toList();
        assertThat(((List<Map<String,Object>>) second.get("items")).stream().map(row -> row.get("id"))).doesNotContainAnyElementsOf(firstIds);
        assertThat(repository.count(1, false, "Запись 26")).isEqualTo(1);
    }
}
