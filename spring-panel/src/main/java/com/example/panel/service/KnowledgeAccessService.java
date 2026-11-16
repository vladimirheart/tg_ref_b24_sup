package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Resolves resource ACLs against the live panel account and its assigned role. */
@Service
@Transactional
public class KnowledgeAccessService {

    public enum ResourceType { SECTION, ARTICLE }
    public enum SubjectType { ROLE, USER }
    public enum AccessLevel { VIEW, EDIT, MANAGE }

    public record Grant(long id, SubjectType subjectType, long subjectId, String subjectLabel, AccessLevel accessLevel) { }
    public record Subject(SubjectType type, long id, String label) { }

    private final JdbcTemplate jdbc;

    public KnowledgeAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public boolean canViewArticle(Authentication authentication, long articleId) {
        return hasAccess(authentication, ResourceType.ARTICLE, articleId, AccessLevel.VIEW);
    }

    @Transactional(readOnly = true)
    public boolean canEditArticle(Authentication authentication, long articleId) {
        return hasAccess(authentication, ResourceType.ARTICLE, articleId, AccessLevel.EDIT);
    }

    @Transactional(readOnly = true)
    public boolean canManageArticle(Authentication authentication, long articleId) {
        return hasAccess(authentication, ResourceType.ARTICLE, articleId, AccessLevel.MANAGE);
    }

    @Transactional(readOnly = true)
    public boolean canViewSection(Authentication authentication, long sectionId) {
        return hasAccess(authentication, ResourceType.SECTION, sectionId, AccessLevel.VIEW);
    }

    @Transactional(readOnly = true)
    public boolean canEditSection(Authentication authentication, long sectionId) {
        return hasAccess(authentication, ResourceType.SECTION, sectionId, AccessLevel.EDIT);
    }

    @Transactional(readOnly = true)
    public boolean canManageSection(Authentication authentication, long sectionId) {
        return hasAccess(authentication, ResourceType.SECTION, sectionId, AccessLevel.MANAGE);
    }

    @Transactional(readOnly = true)
    public boolean canCreateArticle(Authentication authentication) {
        return actor(authentication).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canCreateSection(Authentication authentication) {
        return actor(authentication).filter(actor -> actor.systemAdministrator()).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canViewKnowledgeFile(Authentication authentication, String storedPath) {
        if (!StringUtils.hasText(storedPath)) {
            return false;
        }
        List<Long> articleIds = jdbc.queryForList("""
            SELECT article_id FROM knowledge_article_files
             WHERE stored_path = ? AND article_id IS NOT NULL
             ORDER BY id DESC LIMIT 2
            """, Long.class, storedPath);
        return articleIds.size() == 1 && canViewArticle(authentication, articleIds.get(0));
    }

    @Transactional(readOnly = true)
    public List<Grant> listGrants(ResourceType resourceType, long resourceId, Authentication authentication) {
        if (!canManage(authentication, resourceType, resourceId)) {
            return List.of();
        }
        return jdbc.query("""
            SELECT acl.id, acl.subject_type, acl.subject_id, acl.access_level,
                   CASE WHEN acl.subject_type = 'ROLE' THEN COALESCE(r.name, '[удалённая роль]')
                        ELSE COALESCE(u.full_name, u.username, '[удалённый пользователь]') END AS subject_label
              FROM knowledge_resource_acl acl
              LEFT JOIN roles r ON acl.subject_type = 'ROLE' AND r.id = acl.subject_id
              LEFT JOIN users u ON acl.subject_type = 'USER' AND u.id = acl.subject_id
             WHERE acl.resource_type = ? AND acl.resource_id = ?
             ORDER BY acl.subject_type, subject_label, acl.id
            """, (rs, row) -> new Grant(
                rs.getLong("id"), SubjectType.valueOf(rs.getString("subject_type")), rs.getLong("subject_id"),
                rs.getString("subject_label"), AccessLevel.valueOf(rs.getString("access_level"))),
            resourceType.name(), resourceId);
    }

    @Transactional(readOnly = true)
    public List<Subject> listSubjects(Authentication authentication) {
        if (actor(authentication).isEmpty()) {
            return List.of();
        }
        List<Subject> subjects = new ArrayList<>();
        subjects.addAll(jdbc.query("SELECT id, name FROM roles ORDER BY lower(name), id",
            (rs, row) -> new Subject(SubjectType.ROLE, rs.getLong("id"), "Роль: " + rs.getString("name"))));
        subjects.addAll(jdbc.query("""
            SELECT id, COALESCE(NULLIF(full_name, ''), username) AS label
              FROM users
             WHERE enabled = true AND COALESCE(is_blocked, false) = false
             ORDER BY lower(COALESCE(NULLIF(full_name, ''), username)), id
            """, (rs, row) -> new Subject(SubjectType.USER, rs.getLong("id"), "Пользователь: " + rs.getString("label"))));
        return subjects;
    }

    public void addGrant(ResourceType resourceType, long resourceId, SubjectType subjectType, long subjectId,
                         AccessLevel level, Authentication authentication) {
        Actor actor = requireManager(authentication, resourceType, resourceId);
        if (!subjectExists(subjectType, subjectId)) {
            throw new IllegalArgumentException("Получатель права не найден или отключён");
        }
        if (!hasAnyGrant(resourceType, resourceId)) {
            jdbc.update("""
                INSERT INTO knowledge_resource_acl(resource_type, resource_id, subject_type, subject_id, access_level)
                VALUES (?, ?, 'USER', ?, 'MANAGE')
                ON CONFLICT (resource_type, resource_id, subject_type, subject_id)
                DO UPDATE SET access_level = 'MANAGE'
                """, resourceType.name(), resourceId, actor.userId());
        }
        AccessLevel effectiveLevel = subjectType == SubjectType.USER && subjectId == actor.userId()
            ? AccessLevel.MANAGE : level;
        jdbc.update("""
            INSERT INTO knowledge_resource_acl(resource_type, resource_id, subject_type, subject_id, access_level)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (resource_type, resource_id, subject_type, subject_id)
            DO UPDATE SET access_level = EXCLUDED.access_level
            """, resourceType.name(), resourceId, subjectType.name(), subjectId, effectiveLevel.name());
    }

    public void removeGrant(ResourceType resourceType, long resourceId, long grantId, Authentication authentication) {
        Actor actor = requireManager(authentication, resourceType, resourceId);
        List<String> levels = jdbc.queryForList("""
            SELECT access_level FROM knowledge_resource_acl
             WHERE id = ? AND resource_type = ? AND resource_id = ?
            """, String.class, grantId, resourceType.name(), resourceId);
        if (levels.isEmpty()) {
            return;
        }
        if ("MANAGE".equals(levels.get(0))) {
            Integer managers = jdbc.queryForObject("""
                SELECT COUNT(*) FROM knowledge_resource_acl
                 WHERE resource_type = ? AND resource_id = ? AND access_level = 'MANAGE'
                """, Integer.class, resourceType.name(), resourceId);
            if (managers != null && managers <= 1 && !actor.systemAdministrator()) {
                throw new IllegalArgumentException("У ресурса должен остаться хотя бы один управляющий");
            }
        }
        jdbc.update("DELETE FROM knowledge_resource_acl WHERE id = ? AND resource_type = ? AND resource_id = ?",
            grantId, resourceType.name(), resourceId);
    }

    private boolean hasAccess(Authentication authentication, ResourceType requestedType, long requestedId, AccessLevel required) {
        Optional<Actor> actor = actor(authentication);
        if (actor.isEmpty() || requestedId <= 0 || !resourceExists(requestedType, requestedId)) {
            return false;
        }
        if (actor.get().systemAdministrator()) {
            return true;
        }
        Optional<ResourceRef> protectedResource = resolveProtectedResource(requestedType, requestedId);
        if (protectedResource.isEmpty()) {
            return required != AccessLevel.MANAGE;
        }
        return jdbc.queryForList("""
            SELECT access_level FROM knowledge_resource_acl
             WHERE resource_type = ? AND resource_id = ?
               AND ((subject_type = 'USER' AND subject_id = ?)
                 OR (subject_type = 'ROLE' AND subject_id = ?))
            """, String.class, protectedResource.get().type().name(), protectedResource.get().id(),
            actor.get().userId(), actor.get().roleId()).stream()
            .map(AccessLevel::valueOf)
            .anyMatch(grant -> grant.ordinal() >= required.ordinal());
    }

    private Actor requireManager(Authentication authentication, ResourceType type, long id) {
        Actor actor = actor(authentication).orElseThrow(() -> new IllegalArgumentException("Учётная запись недоступна"));
        if (!hasAccess(authentication, type, id, AccessLevel.MANAGE)) {
            throw new org.springframework.security.access.AccessDeniedException("Недостаточно прав для управления доступом");
        }
        return actor;
    }

    private boolean canManage(Authentication authentication, ResourceType type, long id) {
        return hasAccess(authentication, type, id, AccessLevel.MANAGE);
    }

    private Optional<ResourceRef> resolveProtectedResource(ResourceType requestedType, long requestedId) {
        if (requestedType == ResourceType.ARTICLE) {
            if (hasAnyGrant(ResourceType.ARTICLE, requestedId)) {
                return Optional.of(new ResourceRef(ResourceType.ARTICLE, requestedId));
            }
            List<Long> sectionIds = jdbc.queryForList(
                "SELECT section_id FROM knowledge_articles WHERE id = ?", Long.class, requestedId);
            return sectionIds.size() == 1 ? nearestProtectedSection(sectionIds.get(0)) : Optional.empty();
        }
        return nearestProtectedSection(requestedId);
    }

    private Optional<ResourceRef> nearestProtectedSection(long sectionId) {
        Long current = sectionId;
        int guard = 0;
        while (current != null && current > 0 && guard++ < 64) {
            if (hasAnyGrant(ResourceType.SECTION, current)) {
                return Optional.of(new ResourceRef(ResourceType.SECTION, current));
            }
            List<Long> parents = jdbc.queryForList("SELECT parent_id FROM knowledge_sections WHERE id = ?", Long.class, current);
            current = parents.size() == 1 ? parents.get(0) : null;
        }
        return Optional.empty();
    }

    private boolean hasAnyGrant(ResourceType type, long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_resource_acl WHERE resource_type = ? AND resource_id = ?",
            Integer.class, type.name(), id);
        return count != null && count > 0;
    }

    private boolean resourceExists(ResourceType type, long id) {
        String table = type == ResourceType.ARTICLE ? "knowledge_articles" : "knowledge_sections";
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id);
        return count != null && count == 1;
    }

    private boolean subjectExists(SubjectType type, long id) {
        String table = type == SubjectType.ROLE ? "roles" : "users";
        String enabled = type == SubjectType.USER ? " AND enabled = true AND COALESCE(is_blocked, false) = false" : "";
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?" + enabled, Integer.class, id);
        return count != null && count == 1;
    }

    private Optional<Actor> actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof PanelUserPrincipal principal) || principal.getUserId() <= 0
            || !StringUtils.hasText(principal.getUsername())) {
            return Optional.empty();
        }
        List<Actor> actors = jdbc.query("""
            SELECT id, role_id FROM users
             WHERE id = ? AND username = ? AND enabled = true AND COALESCE(is_blocked, false) = false
            """, (rs, row) -> new Actor(rs.getLong("id"), rs.getObject("role_id", Long.class),
                authentication.getAuthorities().stream().anyMatch(authority ->
                    "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_PORTAL_ADMIN".equals(authority.getAuthority()))),
            principal.getUserId(), principal.getUsername());
        return actors.size() == 1 ? Optional.of(actors.get(0)) : Optional.empty();
    }

    private record Actor(long userId, Long roleId, boolean systemAdministrator) { }
    private record ResourceRef(ResourceType type, long id) { }
}
