package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.model.knowledge.KnowledgeArticleCommand;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlIntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
class KnowledgeAccessServicePostgresqlTest extends PostgresqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KnowledgeAccessService access;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Test
    void restrictsSectionsArticlesAndFilesToGrantedRoleWithoutLeakingToAnotherUser() {
        long roleId = insertRole("kb_access_role_01_290");
        Authentication administrator = authentication(insertUser("kb_access_admin_01_290", null), "kb_access_admin_01_290", "ROLE_ADMIN");
        Authentication permitted = authentication(insertUser("kb_access_member_01_290", roleId), "kb_access_member_01_290", "PAGE_KNOWLEDGE_BASE");
        Authentication denied = authentication(insertUser("kb_access_denied_01_290", null), "kb_access_denied_01_290", "PAGE_KNOWLEDGE_BASE");

        long rootId = jdbc.queryForObject("SELECT id FROM knowledge_sections WHERE code = 'general'", Long.class);
        long privateSectionId = jdbc.queryForObject("""
            INSERT INTO knowledge_sections(parent_id, title, sort_order)
            VALUES (?, 'Закрытый раздел 01-290', 99) RETURNING id
            """, Long.class, rootId);
        long sectionArticleId = insertArticle("Закрытая статья раздела 01-290", privateSectionId);
        long directArticleId = insertArticle("Статья с индивидуальной ACL 01-290", privateSectionId);
        jdbc.update("""
            INSERT INTO knowledge_article_files(article_id, stored_path, original_name, mime_type, file_size)
            VALUES (?, 'kb-access-secret-01-290.pdf', 'secret.pdf', 'application/pdf', 1)
            """, sectionArticleId);

        access.addGrant(KnowledgeAccessService.ResourceType.SECTION, privateSectionId,
            KnowledgeAccessService.SubjectType.ROLE, roleId, KnowledgeAccessService.AccessLevel.VIEW, administrator);
        access.addGrant(KnowledgeAccessService.ResourceType.ARTICLE, directArticleId,
            KnowledgeAccessService.SubjectType.USER, userId(permitted), KnowledgeAccessService.AccessLevel.EDIT, administrator);

        assertThat(access.canViewArticle(permitted, sectionArticleId)).isTrue();
        assertThat(access.canEditArticle(permitted, sectionArticleId)).isFalse();
        assertThat(access.canViewArticle(denied, sectionArticleId)).isFalse();
        assertThat(access.canViewKnowledgeFile(permitted, "kb-access-secret-01-290.pdf")).isTrue();
        assertThat(access.canViewKnowledgeFile(denied, "kb-access-secret-01-290.pdf")).isFalse();

        assertThat(access.canViewArticle(permitted, directArticleId)).isTrue();
        assertThat(access.canEditArticle(permitted, directArticleId)).isTrue();
        assertThat(access.canViewArticle(denied, directArticleId)).isFalse();

        var saved = knowledgeBaseService.saveArticle(new KnowledgeArticleCommand(
            directArticleId, "Статья с индивидуальной ACL 01-290", null, null, "published", null,
            null, null, null, "Обновлено пользователем с прямым EDIT", privateSectionId
        ), permitted);
        assertThat(saved.content()).isEqualTo("Обновлено пользователем с прямым EDIT");

        long permittedGrantId = access.listGrants(KnowledgeAccessService.ResourceType.ARTICLE, directArticleId, administrator).stream()
            .filter(grant -> grant.subjectType() == KnowledgeAccessService.SubjectType.USER
                && grant.subjectId() == userId(permitted))
            .findFirst().orElseThrow().id();
        access.removeGrant(KnowledgeAccessService.ResourceType.ARTICLE, directArticleId, permittedGrantId, administrator);
        long administratorGrantId = access.listGrants(KnowledgeAccessService.ResourceType.ARTICLE, directArticleId, administrator).stream()
            .filter(grant -> grant.subjectType() == KnowledgeAccessService.SubjectType.USER
                && grant.subjectId() == userId(administrator))
            .findFirst().orElseThrow().id();
        access.removeGrant(KnowledgeAccessService.ResourceType.ARTICLE, directArticleId, administratorGrantId, administrator);
        assertThat(access.canEditArticle(permitted, directArticleId)).isFalse();
    }

    private long insertRole(String name) {
        return jdbc.queryForObject("INSERT INTO roles(name, permissions) VALUES (?, '{}') RETURNING id", Long.class, name);
    }

    private long insertUser(String username, Long roleId) {
        return jdbc.queryForObject("""
            INSERT INTO users(username, password, enabled, is_blocked, role_id)
            VALUES (?, 'n/a', true, false, ?) RETURNING id
            """, Long.class, username, roleId);
    }

    private long insertArticle(String title, long sectionId) {
        return jdbc.queryForObject("""
            INSERT INTO knowledge_articles(title, content, section_id, status)
            VALUES (?, 'Текст', ?, 'published') RETURNING id
            """, Long.class, title, sectionId);
    }

    private long userId(Authentication authentication) {
        return ((PanelUserPrincipal) authentication.getPrincipal()).getUserId();
    }

    private Authentication authentication(long userId, String username, String authority) {
        var principal = new PanelUserPrincipal(userId, username, "n/a", true,
            List.of(new SimpleGrantedAuthority(authority), new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE")));
        return new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
    }
}
