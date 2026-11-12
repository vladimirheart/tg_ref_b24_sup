package com.example.panel.passports.api;

import com.example.panel.passports.ObjectPassportDocumentService;
import com.example.panel.passports.ObjectPassportDocumentService.Input;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/object_passports/{passportId}/documents")
@PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
public class ObjectPassportDocumentController {
    private static final String EDIT = "hasAuthority('PAGE_OBJECT_PASSPORTS') and "
            + "T(com.example.panel.security.PanelAccessPolicy).has(authentication, 'EDIT_PASSPORT_DOCUMENTS')";
    private final ObjectPassportDocumentService documents;
    private final ObjectMapper json;

    public ObjectPassportDocumentController(ObjectPassportDocumentService documents, ObjectMapper json) {
        this.documents = documents; this.json = json;
    }

    @GetMapping
    public Map<String, Object> list(@PathVariable long passportId, @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "") String q, @RequestParam(required = false) UUID before, Authentication actor) {
        return documents.list(passportId, archived, q, before, actor);
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize(EDIT)
    public Map<String, Object> create(@PathVariable long passportId, @RequestParam String metadata,
            @RequestParam(required = false) MultipartFile file, Authentication actor) throws IOException {
        return documents.create(passportId, input(metadata), file, actor.getName());
    }

    @PostMapping(value = "/{id}/revisions", consumes = "multipart/form-data")
    @PreAuthorize(EDIT)
    public Map<String, Object> revise(@PathVariable long passportId, @PathVariable UUID id, @RequestParam String metadata,
            @RequestParam(required = false) MultipartFile file, Authentication actor) throws IOException {
        return documents.revise(passportId, id, input(metadata), file, actor.getName());
    }

    @GetMapping("/{id}/revisions")
    public Map<String, Object> history(@PathVariable long passportId, @PathVariable UUID id,
            @RequestParam(required = false) Integer before) { return documents.history(passportId, id, before); }

    @GetMapping("/{id}/revisions/{revision}")
    public Map<String, Object> version(@PathVariable long passportId, @PathVariable UUID id, @PathVariable int revision) {
        return documents.version(passportId, id, revision);
    }

    @GetMapping("/{id}/revisions/{revision}/file")
    public ResponseEntity<Resource> file(@PathVariable long passportId, @PathVariable UUID id, @PathVariable int revision,
            @RequestParam(defaultValue = "false") boolean preview) throws IOException {
        return documents.download(passportId, id, revision, preview);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(EDIT)
    public Map<String, Object> archive(@PathVariable long passportId, @PathVariable UUID id,
            @RequestBody ArchiveInput input, Authentication actor) {
        return documents.archive(passportId, id, input.baseRevision(), input.archived(), actor.getName());
    }

    private Input input(String raw) {
        if (raw.length() > 100_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Слишком много данных в карточке");
        try { return json.readValue(raw, Input.class); }
        catch (JsonProcessingException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте поля и дату документа"); }
    }
    public record ArchiveInput(@com.fasterxml.jackson.annotation.JsonProperty("base_revision") int baseRevision, boolean archived) {}
}
