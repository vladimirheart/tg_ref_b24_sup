# 2026-04-17 17:13:00 - добавлен preview и выбор статей перед импортом из Notion

## Что изменено

- В `KnowledgeBaseNotionService` добавлен отдельный шаг `previewImportArticles`, который собирает список кандидатов на импорт без немедленной загрузки в локальную базу знаний.
- Импорт из Notion теперь умеет принимать список выбранных `externalId` и загружать только отмеченные статьи.
- В `KnowledgeArticleRepository` добавлен пакетный поиск уже импортированных статей по `externalId`, чтобы в preview показывать, будет запись создана или обновлена.
- В `KnowledgeBaseController` добавлен маршрут preview перед импортом и обновлены сообщения о результате выборочного импорта.
- В `knowledge/list.html` добавлен интерфейс preview с чекбоксами, поиском по найденным статьям, кнопками `выбрать все / снять все` и отдельной кнопкой подтверждения импорта.
- В `KnowledgeBaseNotionServiceTest` добавлены тесты на выбор всех найденных страниц по умолчанию и на импорт только отмеченных статей.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/controller/KnowledgeBaseController.java`
- `spring-panel/src/main/java/com/example/panel/repository/KnowledgeArticleRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/KnowledgeBaseNotionService.java`
- `spring-panel/src/main/java/com/example/panel/model/knowledge/KnowledgeNotionImportPreview.java`
- `spring-panel/src/main/java/com/example/panel/model/knowledge/KnowledgeNotionImportPreviewItem.java`
- `spring-panel/src/main/resources/templates/knowledge/list.html`
- `spring-panel/src/test/java/com/example/panel/service/KnowledgeBaseNotionServiceTest.java`
