# 2026-04-17 14:59:00 - добавлен markdown-preview для статей базы знаний

## Что изменено

- В `spring-panel/pom.xml` добавлены зависимости `commonmark` и GFM-расширения для таблиц, автоссылок, зачёркивания и task list.
- Добавлен сервис `KnowledgeMarkdownRenderer`, который преобразует markdown статьи в безопасный HTML c экранированием raw HTML и санитизацией ссылок.
- В `KnowledgeBaseService` и `KnowledgeArticleDetails` добавлено поле `contentHtml`, чтобы шаблон получал готовый предпросмотр импортированного контента.
- В `knowledge/editor.html` рядом с полем текста статьи добавлен блок предпросмотра markdown.
- В `static/css/app.css` добавлены стили для заголовков, списков, блоков кода, цитат, таблиц и изображений внутри markdown-контента.
- Добавлен тест `KnowledgeMarkdownRendererTest` на базовый рендер markdown и экранирование опасного HTML.

## Затронутые файлы

- `spring-panel/pom.xml`
- `spring-panel/src/main/java/com/example/panel/service/KnowledgeMarkdownRenderer.java`
- `spring-panel/src/main/java/com/example/panel/service/KnowledgeBaseService.java`
- `spring-panel/src/main/java/com/example/panel/model/knowledge/KnowledgeArticleDetails.java`
- `spring-panel/src/main/java/com/example/panel/controller/KnowledgeBaseController.java`
- `spring-panel/src/main/resources/templates/knowledge/editor.html`
- `spring-panel/src/main/resources/static/css/app.css`
- `spring-panel/src/test/java/com/example/panel/service/KnowledgeMarkdownRendererTest.java`
