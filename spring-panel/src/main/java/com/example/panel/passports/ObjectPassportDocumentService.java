package com.example.panel.passports;

import com.example.panel.passports.ObjectPassportDocumentStorage.FileInfo;
import com.example.panel.passports.ObjectPassportDocumentStorage.PreparedFile;
import com.example.panel.security.PanelAccessPolicy;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectPassportDocumentService {
    private final ObjectPassportDocumentRepository repository;
    private final ObjectPassportDocumentStorage storage;
    private final TransactionTemplate transactions;

    public ObjectPassportDocumentService(ObjectPassportDocumentRepository repository, ObjectPassportDocumentStorage storage,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Map<String, Object> list(long passportId, boolean archived, String query, UUID before, Authentication actor) {
        repository.requirePassport(passportId, false);
        String search = clean(query, 200, "Поиск");
        List<ObjectPassportDocument> found = repository.list(passportId, archived, search, before, 26);
        boolean more = found.size() > 25;
        var visible = found.subList(0, Math.min(found.size(), 25));
        return Map.of("success", true, "items", visible.stream().map(item -> payload(item, false)).toList(), "has_more", more,
                "next_cursor", more ? visible.get(visible.size()-1).id().toString() : "", "total", repository.count(passportId, archived, search),
                "can_edit", PanelAccessPolicy.has(actor, "EDIT_PASSPORT_DOCUMENTS"), "max_file_size", ObjectPassportDocumentStorage.MAX_BYTES);
    }

    public Map<String, Object> history(long passportId, UUID id, Integer before) {
        repository.current(passportId, id, false);
        if (before != null && before < 1) throw bad("Некорректная версия");
        var found = repository.history(passportId, id, before == null ? Integer.MAX_VALUE : before, 26);
        boolean more = found.size() > 25;
        var visible = found.subList(0, Math.min(found.size(), 25));
        return Map.of("success", true, "items", visible.stream().map(item -> payload(item, false)).toList(),
                "has_more", more, "next_cursor", more ? visible.get(visible.size()-1).revision() : 0);
    }

    public Map<String, Object> version(long passportId, UUID id, int revision) {
        return Map.of("success", true, "document", payload(repository.version(passportId, id, revision), true));
    }

    public Map<String, Object> create(long passportId, Input input, MultipartFile file, String actor) throws IOException {
        Input validated = validate(input);
        PreparedFile prepared = storage.prepare(file);
        try {
            return transactions.execute(status -> {
                repository.requirePassport(passportId, true);
                UUID id = UUID.randomUUID();
                var document = document(id, passportId, 1, validated, store(passportId, id, prepared), false, "create", actor);
                repository.create(document);
                return Map.of("success", true, "document", payload(document, true));
            });
        } catch (UncheckedIOException ex) { throw ex.getCause(); }
    }

    public Map<String, Object> revise(long passportId, UUID id, Input input, MultipartFile file, String actor) throws IOException {
        Input validated = validate(input);
        PreparedFile prepared = storage.prepare(file);
        try {
            return transactions.execute(status -> {
                var current = repository.current(passportId, id, true);
                expected(current, validated.baseRevision());
                if (current.archived()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Сначала верните документ из архива");
                FileInfo replacement = prepared == null ? current.file() : store(passportId, id, prepared);
                var next = document(id, passportId, current.revision()+1, validated, replacement, false, "edit", actor);
                repository.append(next, current.revision());
                return Map.of("success", true, "document", payload(next, true));
            });
        } catch (UncheckedIOException ex) { throw ex.getCause(); }
    }

    public Map<String, Object> archive(long passportId, UUID id, int revision, boolean archived, String actor) {
        return transactions.execute(status -> {
            var current = repository.current(passportId, id, true);
            expected(current, revision);
            if (current.archived() == archived) return Map.of("success", true, "document", payload(current, true));
            Input metadata = new Input(revision, current.title(), current.category(), current.eventDate(), current.description(), current.fields());
            var next = document(id, passportId, revision+1, metadata, current.file(), archived, archived ? "archive" : "unarchive", actor);
            repository.append(next, revision);
            return Map.of("success", true, "document", payload(next, true));
        });
    }

    public ResponseEntity<Resource> download(long passportId, UUID id, int revision, boolean preview) throws IOException {
        var version = repository.version(passportId, id, revision);
        if (version.file() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "У записи нет файла");
        return storage.download(version.file(), preview);
    }

    private FileInfo store(long passportId, UUID id, PreparedFile prepared) {
        if (prepared == null) return null;
        try {
            FileInfo file = storage.store(passportId, id, prepared);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) storage.discard(file.key());
                }
            });
            return file;
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }

    private ObjectPassportDocument document(UUID id, long passportId, int revision, Input input, FileInfo file,
            boolean archived, String action, String actor) {
        return new ObjectPassportDocument(id, passportId, revision, input.title(), input.category(), input.eventDate(),
                input.description(), input.fields(), archived, file, action, actor, OffsetDateTime.now());
    }

    private Input validate(Input input) {
        if (input == null) throw bad("Заполните карточку документа");
        String title = clean(input.title(), 240, "Название");
        if (title.isEmpty()) throw bad("Введите название");
        Map<String, String> fields = new LinkedHashMap<>();
        if (input.fields() != null) {
            if (input.fields().size() > 30) throw bad("Не более 30 дополнительных полей");
            input.fields().forEach((key, value) -> {
                String label = clean(key, 80, "Название поля");
                if (label.isEmpty() || fields.containsKey(label)) throw bad("Названия дополнительных полей должны быть непустыми и уникальными");
                fields.put(label, clean(value, 2000, "Значение поля"));
            });
        }
        return new Input(input.baseRevision(), title, clean(input.category(), 120, "Тип записи"), input.eventDate(),
                clean(input.description(), 20_000, "Описание"), fields);
    }

    private void expected(ObjectPassportDocument current, int revision) {
        if (revision != current.revision()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Документ уже изменён. Обновите его перед сохранением.");
    }
    private String clean(String value, int max, String label) {
        String result = value == null ? "" : value.trim();
        if (result.length() > max || result.indexOf('\0') >= 0) throw bad(label + ": превышена допустимая длина или есть недопустимые символы");
        return result;
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private Map<String, Object> payload(ObjectPassportDocument d, boolean previewText) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", d.id().toString()); result.put("revision", d.revision()); result.put("title", d.title());
        result.put("category", d.category()); result.put("event_date", d.eventDate() == null ? "" : d.eventDate().toString());
        result.put("description", d.description()); result.put("fields", d.fields()); result.put("archived", d.archived());
        result.put("action", d.action()); result.put("actor", d.actor()); result.put("changed_at", d.changedAt().toString());
        if (d.file() != null) {
            var file = d.file();
            String url = "/api/object_passports/" + d.passportId() + "/documents/" + d.id() + "/revisions/" + d.revision() + "/file";
            var details = new LinkedHashMap<String, Object>();
            details.put("name", file.name()); details.put("size", file.size()); details.put("media_type", file.mediaType());
            details.put("preview_kind", file.previewKind()); details.put("download_url", url);
            if (List.of("image", "pdf").contains(file.previewKind())) details.put("preview_url", url + "?preview=true");
            if (previewText && "text".equals(file.previewKind())) details.put("preview_text", file.previewText());
            result.put("file", details);
        }
        return result;
    }

    public record Input(@JsonProperty("base_revision") int baseRevision, String title, String category,
            @JsonProperty("event_date") LocalDate eventDate, String description, Map<String, String> fields) {}
}
