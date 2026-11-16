# 01-290 — sections, ACL and wiki links for the knowledge base

## User prompt

```text
нужно немного поработать по базе знаний.
в базе знаний нужны статьи, доступные только определённым ролям и\или пользователям. возможно даже отдельные разделы БЗ
сделать общие связки как в obsidian?

звучит неплохо. делай, но не распыляйся не незначиемые мелочи. выполни задачу качественно и быстро
```

## Changed

- Added PostgreSQL migration V95 with knowledge sections, resource ACL and persistent article-to-article links. Existing articles are put into the public `general` section.
- Added server-side access resolution for roles and named users, including inherited section permissions and direct article overrides. Article lists, direct URLs, edits, links and attachment downloads use it.
- A system administrator can remove the final ACL grant to restore inherited access; a regular manager cannot lock a resource without a manager.
- Added section and ACL management UI; an article can be kept editable by a direct grantee without allowing it to be moved to the hidden section.
- Added Obsidian-style `[[Title]]` / `[[Title|label]]` links, stable target IDs, outgoing links and backlinks. Inaccessible targets are not named or linked.
- Preserved current Notion import flow and existing open knowledge base behavior when no ACL is defined.

## Key files

- `spring-panel/src/main/resources/db/migration/postgresql/V95__knowledge_sections_access_and_links.sql`
- `spring-panel/src/main/java/com/example/panel/service/KnowledgeAccessService.java`
- `spring-panel/src/main/java/com/example/panel/service/KnowledgeSectionService.java`
- `spring-panel/src/main/java/com/example/panel/service/KnowledgeBaseService.java`
- `spring-panel/src/main/java/com/example/panel/controller/KnowledgeBaseController.java`
- `spring-panel/src/main/java/com/example/panel/controller/AttachmentController.java`
- `spring-panel/src/main/resources/templates/knowledge/{list,editor,section-editor}.html`
- `spring-panel/src/test/java/com/example/panel/service/KnowledgeAccessServicePostgresqlTest.java`

## Validation

- `./mvnw.cmd -q "-Dtest=KnowledgeBaseServiceTest,KnowledgeBaseControllerWebMvcTest,KnowledgeNotesSourceContractTest,KnowledgeAccessServicePostgresqlTest" test` — passed.
- Testcontainers PostgreSQL started with an empty schema, validated and applied all 95 Flyway migrations through V95; the ACL test covered role access, inheritance, protected attachment access and edit through a direct grant without section-edit access.
- `git diff --check` — passed.

## Delivery

- Source change only. No production migration, deployment, commit or push was performed.
