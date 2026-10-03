package com.example.panel.service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeNoteLocationService {

    private final JdbcTemplate jdbcTemplate;
    private final IikoDepartmentLocationCatalogService locationCatalogService;
    private final KnowledgeNoteService knowledgeNoteService;
    private final DialogLookupReadService dialogLookupReadService;

    public KnowledgeNoteLocationService(JdbcTemplate jdbcTemplate,
                                        IikoDepartmentLocationCatalogService locationCatalogService,
                                        KnowledgeNoteService knowledgeNoteService,
                                        DialogLookupReadService dialogLookupReadService) {
        this.jdbcTemplate = jdbcTemplate;
        this.locationCatalogService = locationCatalogService;
        this.knowledgeNoteService = knowledgeNoteService;
        this.dialogLookupReadService = dialogLookupReadService;
    }

    @Transactional
    public long saveNote(Long id,
                         String title,
                         String body,
                         List<String> customFieldKeys,
                         List<String> customFieldValues,
                         List<Long> knowledgeArticleIds,
                         List<Long> objectPassportIds,
                         List<String> locationNames,
                         String actor) {
        long noteId = knowledgeNoteService.save(
            id, title, body, customFieldKeys, customFieldValues, knowledgeArticleIds, objectPassportIds, actor
        );
        replaceLocationLinks(noteId, locationNames);
        return noteId;
    }

    @Transactional(readOnly = true)
    public Set<String> loadLocationNames(Long noteId) {
        if (noteId == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(loadLocationNamesByNote(noteId));
    }

    @Transactional(readOnly = true)
    public List<LocationOption> listLocationOptions(Long noteId) {
        LinkedHashMap<String, LocationOption> result = new LinkedHashMap<>();
        try {
            Map<String, Object> payload = locationCatalogService.buildEffectiveLocationsPayload(locationCatalogService.loadCatalog());
            flattenCatalog(payload == null ? null : payload.get("tree"), result);
        } catch (RuntimeException ignored) {
            // Existing location links stay editable even when the catalog is temporarily unavailable.
        }
        if (noteId != null) {
            for (String locationName : loadLocationNamesByNote(noteId)) {
                result.putIfAbsent(locationName, new LocationOption(locationName, locationName));
            }
        }
        return result.values().stream()
            .sorted(Comparator.comparing(LocationOption::label, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<DialogNote> listDialogNotes(String ticketId, String operator) {
        String normalizedTicketId = clean(ticketId);
        if (normalizedTicketId == null) {
            return List.of();
        }
        var dialog = dialogLookupReadService.findDialog(normalizedTicketId, operator);
        if (dialog.isEmpty()) {
            return List.of();
        }
        String locationName = clean(dialog.get().locationName());
        if (locationName != null) {
            return jdbcTemplate.query(
                """
                SELECT n.id, n.title, n.body, n.updated_at,
                       EXISTS (
                           SELECT 1
                             FROM knowledge_note_location_links l
                            WHERE l.note_id = n.id
                              AND l.location_name = ?
                       ) AS location_matched
                  FROM knowledge_notes n
                 ORDER BY location_matched DESC, n.updated_at DESC, n.id DESC
                """,
                (rs, rowNum) -> new DialogNote(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("body") == null ? "" : rs.getString("body"),
                    rs.getObject("updated_at", OffsetDateTime.class),
                    rs.getBoolean("location_matched")
                ),
                locationName
            );
        }
        return jdbcTemplate.query(
            """
            SELECT n.id, n.title, n.body, n.updated_at, FALSE AS location_matched
              FROM knowledge_notes n
             ORDER BY n.updated_at DESC, n.id DESC
            """,
            (rs, rowNum) -> new DialogNote(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("body") == null ? "" : rs.getString("body"),
                rs.getObject("updated_at", OffsetDateTime.class),
                false
            )
        );
    }

    private void replaceLocationLinks(long noteId, List<String> rawLocationNames) {
        jdbcTemplate.update("DELETE FROM knowledge_note_location_links WHERE note_id = ?", noteId);
        LinkedHashSet<String> locationNames = new LinkedHashSet<>();
        if (rawLocationNames != null) {
            for (String rawLocationName : rawLocationNames) {
                String locationName = clean(rawLocationName);
                if (locationName != null) {
                    locationNames.add(locationName);
                }
            }
        }
        for (String locationName : locationNames) {
            jdbcTemplate.update(
                """
                INSERT INTO knowledge_note_location_links(note_id, location_name, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (note_id, location_name) DO NOTHING
                """,
                noteId, locationName
            );
        }
    }

    private List<String> loadLocationNamesByNote(long noteId) {
        return jdbcTemplate.query(
            """
            SELECT location_name
              FROM knowledge_note_location_links
             WHERE note_id = ?
             ORDER BY location_name, id
            """,
            (rs, rowNum) -> rs.getString("location_name"),
            noteId
        );
    }

    private void flattenCatalog(Object rawTree, Map<String, LocationOption> target) {
        if (!(rawTree instanceof Map<?, ?> businesses)) {
            return;
        }
        for (Object rawTypes : businesses.values()) {
            if (!(rawTypes instanceof Map<?, ?> types)) continue;
            for (Object rawCities : types.values()) {
                if (!(rawCities instanceof Map<?, ?> cities)) continue;
                for (Object rawLocations : cities.values()) {
                    appendLocations(target, rawLocations);
                }
            }
        }
    }

    private void appendLocations(Map<String, LocationOption> target, Object rawLocations) {
        if (rawLocations instanceof Collection<?> locations) {
            for (Object rawLocation : locations) {
                addOption(target, rawLocation);
            }
        } else if (rawLocations instanceof Map<?, ?> locations) {
            for (Object rawLocation : locations.keySet()) {
                addOption(target, rawLocation);
            }
        }
    }

    private void addOption(Map<String, LocationOption> target, Object rawLocation) {
        String locationName = clean(rawLocation);
        if (locationName == null) return;
        target.putIfAbsent(locationName, new LocationOption(locationName, locationName));
    }

    private String clean(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "—".equals(text) ? null : text;
    }

    public record LocationOption(String name, String label) { }

    public record DialogNote(Long id,
                             String title,
                             String body,
                             OffsetDateTime updatedAt,
                             boolean locationMatched) { }
}
