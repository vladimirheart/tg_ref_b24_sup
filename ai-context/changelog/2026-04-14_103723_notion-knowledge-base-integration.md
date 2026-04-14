# 2026-04-14 10:37:23 - внутренняя интеграция Notion для базы знаний

## Что изменено

- Добавлен task `[01-016]` и его детализация для внутренней интеграции Notion.
- В сущность `knowledge_articles` добавлены поля внешнего источника для upsert
  статей из Notion: источник, внешний ID, URL и дата обновления.
- Добавлена SQLite-миграция `V29__extend_knowledge_articles_for_external_import`
  для новых полей `knowledge_articles`.
- Реализован `KnowledgeBaseNotionService` для:
  подключения к Notion по integration token,
  разрешения database/data source,
  фильтрации страниц по авторам,
  получения тела страницы в markdown,
  импорта и обновления статей в локальной базе знаний.
- Добавлена форма `KnowledgeBaseNotionConfigForm` для сохранения настроек
  интеграции.
- Контроллер базы знаний расширен действиями:
  сохранение настроек Notion,
  проверка подключения,
  запуск импорта статей.
- На странице `knowledge/list.html` добавлен UI-блок интеграции Notion прямо в
  разделе базы знаний.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-016.md`
- `spring-panel/src/main/java/com/example/panel/controller/KnowledgeBaseController.java`
- `spring-panel/src/main/java/com/example/panel/entity/KnowledgeArticle.java`
- `spring-panel/src/main/java/com/example/panel/model/knowledge/KnowledgeBaseNotionConfigForm.java`
- `spring-panel/src/main/java/com/example/panel/repository/KnowledgeArticleRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/KnowledgeBaseNotionService.java`
- `spring-panel/src/main/java/db/migration/sqlite/V29__extend_knowledge_articles_for_external_import.java`
- `spring-panel/src/main/resources/templates/knowledge/list.html`
