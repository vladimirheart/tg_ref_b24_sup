package com.example.panel.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class KnowledgeSectionService {

    public record Section(long id, Long parentId, String code, String title, int sortOrder, int depth) { }

    private final JdbcTemplate jdbc;
    private final KnowledgeAccessService access;

    public KnowledgeSectionService(JdbcTemplate jdbc, KnowledgeAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<Section> listVisible(Authentication authentication) {
        List<Section> sections = listAll();
        return sections.stream()
            .filter(section -> access.canViewSection(authentication, section.id()))
            .map(section -> sectionWithDepth(section, sections))
            .sorted(Comparator.comparingInt(Section::sortOrder).thenComparing(Section::title, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Section> findVisible(long id, Authentication authentication) {
        return listAll().stream()
            .filter(section -> section.id() == id && access.canViewSection(authentication, id))
            .findFirst()
            .map(section -> sectionWithDepth(section, listAll()));
    }

    public Section save(Long id, String title, Long parentId, Authentication authentication) {
        String normalizedTitle = title == null ? "" : title.trim();
        if (!StringUtils.hasText(normalizedTitle) || normalizedTitle.length() > 500) {
            throw new IllegalArgumentException("Укажите название раздела длиной до 500 символов");
        }
        if (id == null) {
            if (!access.canCreateSection(authentication)) {
                throw new AccessDeniedException("Создавать разделы может системный администратор");
            }
            Long actualParentId = normalizeParent(parentId, null);
            Long inserted = jdbc.queryForObject("""
                INSERT INTO knowledge_sections(parent_id, title, sort_order, created_at, updated_at)
                VALUES (?, ?, COALESCE((SELECT MAX(sort_order) + 1 FROM knowledge_sections WHERE parent_id IS NOT DISTINCT FROM ?), 0),
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, actualParentId, normalizedTitle, actualParentId);
            return load(inserted).orElseThrow();
        }
        Section current = load(id).orElseThrow(() -> new IllegalArgumentException("Раздел не найден"));
        if (!access.canManageSection(authentication, current.id())) {
            throw new AccessDeniedException("Недостаточно прав для изменения раздела");
        }
        Long actualParentId = normalizeParent(parentId, current.id());
        jdbc.update("""
            UPDATE knowledge_sections
               SET title = ?, parent_id = ?, updated_at = CURRENT_TIMESTAMP
             WHERE id = ?
            """, normalizedTitle, actualParentId, current.id());
        return load(current.id()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<Section> load(long id) {
        List<Section> rows = jdbc.query("""
            SELECT id, parent_id, code, title, sort_order
              FROM knowledge_sections WHERE id = ?
            """, (rs, row) -> new Section(rs.getLong("id"), rs.getObject("parent_id", Long.class),
                rs.getString("code"), rs.getString("title"), rs.getInt("sort_order"), 0), id);
        return rows.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<Section> listAll() {
        return jdbc.query("""
            SELECT id, parent_id, code, title, sort_order
              FROM knowledge_sections
             ORDER BY sort_order, lower(title), id
            """, (rs, row) -> new Section(rs.getLong("id"), rs.getObject("parent_id", Long.class),
                rs.getString("code"), rs.getString("title"), rs.getInt("sort_order"), 0));
    }

    private Long normalizeParent(Long parentId, Long currentId) {
        if (parentId == null || parentId <= 0) {
            return null;
        }
        if (currentId != null && parentId.equals(currentId)) {
            throw new IllegalArgumentException("Раздел не может быть родителем самому себе");
        }
        Section parent = load(parentId).orElseThrow(() -> new IllegalArgumentException("Родительский раздел не найден"));
        if (currentId != null && isDescendant(parent.id(), currentId)) {
            throw new IllegalArgumentException("Нельзя перенести раздел в его дочерний раздел");
        }
        return parent.id();
    }

    private boolean isDescendant(long candidateId, long ancestorId) {
        Long current = candidateId;
        int guard = 0;
        while (current != null && guard++ < 64) {
            if (current == ancestorId) {
                return true;
            }
            current = load(current).map(Section::parentId).orElse(null);
        }
        return false;
    }

    private Section sectionWithDepth(Section section, List<Section> all) {
        int depth = 0;
        Long parent = section.parentId();
        while (parent != null && depth < 16) {
            final Long expected = parent;
            Optional<Section> found = all.stream().filter(item -> item.id() == expected).findFirst();
            if (found.isEmpty()) {
                break;
            }
            depth++;
            parent = found.get().parentId();
        }
        return new Section(section.id(), section.parentId(), section.code(), section.title(), section.sortOrder(), depth);
    }
}
