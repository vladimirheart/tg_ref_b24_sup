# Knowledge rendered markdown cleanup and inline media rewrite

- `KnowledgeBaseService` теперь нормализует Notion-markdown перед рендером: удаляет `table_of_contents`, `empty-block` и превращает literal `<br>` в переносы строк;
- встроенные media URL внутри markdown подменяются на локальные `/api/attachments/knowledge-base/...`, если соответствующий файл уже импортирован как notion-вложение;
- `KnowledgeBaseNotionService` начал считать изображения частью импортируемых вложений: добавлены image-blocks и image-расширения в список поддерживаемых URL;
- в `knowledge/editor.html` кнопка `Редактировать markdown` переведена на явный JS toggle с bootstrap fallback, чтобы раскрытие панели не зависело от неинициализированного `data-bs-toggle`;
- добавлен unit-тест на очистку Notion-тегов и на подмену встроенной media-ссылки в локальный attachment endpoint.
